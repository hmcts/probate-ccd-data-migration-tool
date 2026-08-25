package uk.gov.hmcts.reform.migration.reimpl.service;

import feign.FeignException;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

@Component
@Slf4j
public class ElasticSearchHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final ReimplConfig reimplConfig;

    private static final int SEARCH_MAX_ATTEMPTS = 3;
    private static final int SEARCH_RETRY_DELAY_SECONDS = 2;

    public ElasticSearchHandler(CoreCaseDataApi coreCaseDataApi,
                                final ReimplConfig reimplConfig) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.reimplConfig = Objects.requireNonNull(reimplConfig);
    }

    /**
     * Executes the case search using a query source that does not accept a
     * page size.
     *
     * <p>The configured maximum-results limit is not applied to this overload
     * because the query source cannot adjust the Elasticsearch page size.</p>
     *
     * @param migrationId identifier used in search logging
     * @param userToken IDAM user token
     * @param s2sToken service-to-service token
     * @param caseType CCD case type being searched
     * @param querySource creates the query using the previous page's highest case reference
     * @return matching cases
     * @deprecated use the page-size-aware {@link #searchCases(String, UserToken, S2sToken,
     *     CaseType, BiFunction)} overload so that the configured maximum-results limit
     *     can be applied
     */
    @Deprecated
    public Set<CaseSummary> searchCases(
            final String migrationId,
            final UserToken userToken,
            final S2sToken s2sToken,
            final CaseType caseType,
            final Function<Optional<Long>, JSONObject> querySource) {

        return executeSearch(
            migrationId,
            userToken,
            s2sToken,
            caseType,
            OptionalInt.empty(),
            (ignoredPageSize, fromReference) -> querySource.apply(fromReference)
        );
    }

    /**
     * Executes a page-size-aware case search using the maximum-results setting
     * from {@link ReimplConfig}.
     *
     * <p>The configured maximum applies to this search invocation only. For example,
     * if a migration invokes this method once for each case type, a limit of 100 can
     * return up to 100 cases per case type rather than 100 cases across the complete
     * migration.</p>
     *
     * @param migrationId identifier used in search logging
     * @param userToken IDAM user token
     * @param s2sToken service-to-service token
     * @param caseType CCD case type being searched
     * @param querySource creates a query using the requested page size and the
     *                    previous page's highest case reference
     * @return matching cases, limited by the configured maximum when present
     */
    public Set<CaseSummary> searchCases(
            final String migrationId,
            final UserToken userToken,
            final S2sToken s2sToken,
            final CaseType caseType,
            final BiFunction<Integer, Optional<Long>, JSONObject> querySource) {

        return executeSearch(
            migrationId,
            userToken,
            s2sToken,
            caseType,
            reimplConfig.getMaximumResults(),
            querySource
        );
    }

    /**
     * Performs one paginated Elasticsearch search for a single case type.
     *
     * <p>When a maximum is supplied, page sizes are reduced as the search
     * approaches the limit and pagination stops once that many unique cases have
     * been collected.</p>
     */
    private Set<CaseSummary> executeSearch(
            final String migrationId,
            final UserToken userToken,
            final S2sToken s2sToken,
            final CaseType caseType,
            final OptionalInt maximumResults,
            final BiFunction<Integer, Optional<Long>, JSONObject> querySource) {

        final int maximum = maximumResults.orElse(Integer.MAX_VALUE);

        if (maximum < 0) {
            throw new IllegalArgumentException(
                "Maximum results cannot be negative"
            );
        }
        if (maximum == 0) {
            log.info("{}: maximum results is zero, skipping {} case search",
                    migrationId,
                    caseType
            );
            return Set.of();
        }
        log.info("{}: limiting {} candidate search to {} cases; ",
                migrationId,
                caseType,
                maximum);

        final int initialPageSize = Math.min(
            reimplConfig.getQuerySize(),
            maximum
        );

        final JSONObject initialQuery = querySource.apply(initialPageSize, Optional.empty());

        log.info("{}: initial query for {} cases with page size {}",
                migrationId,
                caseType,
                initialPageSize);
        final SearchResult initialResult = searchCasesWithRetry(
                migrationId,
                userToken,
                s2sToken,
                caseType,
                initialQuery);

        if (initialResult == null
                || initialResult.getTotal() == 0) {
            log.info("{}: initial query found no {} cases",
                    migrationId,
                    caseType);
            return Set.of();
        }

        final List<CaseDetails> initialCases = initialResult.getCases();
        log.info("{}: initial query found {} {} cases",
                migrationId,
                initialCases.size(),
                caseType);

        final Set<CaseSummary> candidateCases = new HashSet<>();

        for (final CaseDetails cd : initialCases) {
            if (candidateCases.size() >= maximum) {
                break;
            }
            candidateCases.add(new CaseSummary(cd.getId(), caseType));
        }
        Long highestCaseRef = initialCases.getLast().getId();

        boolean keepSearching = candidateCases.size() < maximum;

        while (keepSearching) {
            final int remaining = maximum - candidateCases.size();

            final int nextPageSize = Math.min(
                reimplConfig.getQuerySize(),
                remaining
            );

            final JSONObject nextQuery = querySource.apply(
                nextPageSize,
                Optional.of(highestCaseRef)
            );
            log.info("{}: searching for next {} cases with page size {}",
                    migrationId,
                    caseType,
                    nextPageSize);
            final SearchResult nextResult = searchCasesWithRetry(
                    migrationId,
                    userToken,
                    s2sToken,
                    caseType,
                    nextQuery);

            if (nextResult == null) {
                keepSearching = false;
                log.info("{}: next {} search returned null",
                        migrationId,
                        caseType);
            } else {
                final List<CaseDetails> nextCases = nextResult.getCases();
                log.info("{}: next {} search found {} cases",
                        migrationId,
                        caseType,
                        nextCases.size());

                if (nextCases.isEmpty()) {
                    keepSearching = false;
                } else {
                    log.info("{}: next {} search returned {} cases",
                            migrationId,
                            caseType,
                            nextCases.size()
                    );
                    for (final CaseDetails cd : nextCases) {
                        if (candidateCases.size() >= maximum) {
                            keepSearching = false;
                            break;
                        }
                        candidateCases.add(new CaseSummary(cd.getId(), caseType));
                        highestCaseRef = cd.getId();
                    }

                    if (candidateCases.size() >= maximum) {
                        keepSearching = false;
                    }
                }
            }
        }
        if (maximumResults.isPresent()
            && candidateCases.size() >= maximum) {
            log.info("{}: reached configured maximum of {} candidate cases for {}",
                    migrationId,
                    maximum,
                    caseType);
        }
        return Set.copyOf(candidateCases);
    }

    private SearchResult searchCasesWithRetry(
        final String migrationId,
        final UserToken userToken,
        final S2sToken s2sToken,
        final CaseType caseType,
        final JSONObject query) {

        for (int attempt = 1; attempt <= SEARCH_MAX_ATTEMPTS; attempt++) {
            try {
                return coreCaseDataApi.searchCases(
                    userToken.getBearerToken(),
                    s2sToken.s2sToken(),
                    caseType.getCcdValue(),
                    query.toString()
                );
            } catch (final FeignException e) {
                if (!isRetryable(e) || attempt == SEARCH_MAX_ATTEMPTS) {
                    throw e;
                }

                final long delaySeconds = (long) SEARCH_RETRY_DELAY_SECONDS * attempt;

                log.warn(
                    "{}: {} case search failed with status {} on attempt {}/{}; retrying in {} seconds",
                    migrationId,
                    caseType,
                    e.status(),
                    attempt,
                    SEARCH_MAX_ATTEMPTS,
                    delaySeconds
                );

                delayBeforeRetry(delaySeconds);
            }
        }

        throw new IllegalStateException("Unexpected end of CCD search retry loop");
    }

    private boolean isRetryable(final FeignException e) {
        return e instanceof RetryableException
            || e.status() == 502
            || e.status() == 503
            || e.status() == 504;
    }

    void delayBeforeRetry(final long delaySeconds) {
        try {
            Thread.sleep(Duration.ofSeconds(delaySeconds));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while waiting to retry CCD case search",
                e
            );
        }
    }
}
