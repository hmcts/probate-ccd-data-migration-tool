package uk.gov.hmcts.reform.migration.reimpl.service;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Component
@Slf4j
public class ElasticSearchHandler {
    private final CoreCaseDataApi coreCaseDataApi;

    public ElasticSearchHandler(CoreCaseDataApi coreCaseDataApi) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
    }

    public Set<CaseSummary> searchCases(
            final String migrationId,
            final UserToken userToken,
            final S2sToken s2sToken,
            final CaseType caseType,
            final Function<Optional<Long>, JSONObject> querySource) {
        final JSONObject initialQuery = querySource.apply(Optional.empty());

        log.info("{}: initial query for {} cases",
                migrationId,
                caseType);
        final SearchResult initialResult = coreCaseDataApi.searchCases(
                userToken.getBearerToken(),
                s2sToken.s2sToken(),
                caseType.getCcdValue(),
                initialQuery.toString());

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
            candidateCases.add(new CaseSummary(cd.getId(), caseType));
        }
        Long highestCaseRef = initialCases.getLast().getId();

        boolean keepSearching = true;
        while (keepSearching) {
            final JSONObject nextQuery = querySource.apply(Optional.of(highestCaseRef));

            log.info("{}: searching for next {} cases",
                    migrationId,
                    caseType);
            final SearchResult nextResult = coreCaseDataApi.searchCases(
                    userToken.getBearerToken(),
                    s2sToken.s2sToken(),
                    caseType.getCcdValue(),
                    nextQuery.toString());

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
                    for (final CaseDetails cd : nextCases) {
                        candidateCases.add(new CaseSummary(cd.getId(), caseType));
                    }
                    highestCaseRef = nextCases.getLast().getId();
                }
            }
        }
        return Set.copyOf(candidateCases);
    }
}
