package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5005;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.SearchResult;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
public class Dtspb5005MigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;

    private final Dtspb5005Config config;
    private final Dtspb5005ElasticQueries elasticQueries;

    private static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    private static final String CAVEAT = "Caveat";
    private static final String JURISDICTION = "PROBATE";
    private static final String APPLICANT_ORGANISATION_POLICY = "applicantOrganisationPolicy";

    static final String EVENT_SUMMARY = "DTSPB-5005 - Add metadata for Notice of Change";
    static final String EVENT_DESCRIPTION = "Add metadata for Notice of Change";

    public Dtspb5005MigrationHandler(
            final CoreCaseDataApi coreCaseDataApi,
            final Dtspb5005Config config,
            final Dtspb5005ElasticQueries elasticQueries) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);

        this.config = Objects.requireNonNull(config);
        this.elasticQueries = Objects.requireNonNull(elasticQueries);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken) {
        final Set<CaseSummary> candidateCases = new HashSet<>();

        candidateCases.addAll(getGorCases(userToken, s2sToken));
        candidateCases.addAll(getCaveatCases(userToken, s2sToken));

        return candidateCases;
    }

    @Override
    public MigrationEvent startEventForCase(
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken) {

        final EventDetails eventDetails = switch (caseSummary.type()) {
            case GRANT_OF_REPRESENTATION -> new EventDetails(
                    GRANT_OF_REPRESENTATION,
                    "boHistoryCorrection");
            case CAVEAT -> new EventDetails(
                    CAVEAT,
                    "boHistoryCorrection");
        };

        final UserDetails userDetails = userToken.userDetails();

        log.info("DTSPB-5005 start event for {} case {}",
                eventDetails.caseType(),
                caseSummary.reference());
        final StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
                userToken.getBearerToken(),
                s2sToken.s2sToken(),
                userDetails.getId(),
                JURISDICTION,
                eventDetails.caseType(),
                caseSummary.reference().toString(),
                eventDetails.eventId());

        return new MigrationEvent(
                caseSummary,
                startEventResponse,
                userToken,
                s2sToken);
    }

    @Override
    public boolean shouldMigrateCase(
            final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final CaseDetails caseDetails = migrationEvent.startEventResponse().getCaseDetails();
        if (caseDetails == null) {
            log.error("DTSPB-5005: No case details present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new RuntimeException("No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5005: No case data present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new RuntimeException("No case data present in startEventResponse for " + caseSummary.reference());
        }

        final boolean hasApplOrgPolicy = caseData.containsKey(APPLICANT_ORGANISATION_POLICY);
        if (hasApplOrgPolicy) {
            log.info("DTSPB-5005: {} case {} already has applicantOrganisationPolicy so no migration needed",
                    caseSummary.type(),
                    caseSummary.reference());
        }
        return !hasApplOrgPolicy;
    }

    @Override
    public boolean migrate(
            final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();

        final CaseDetails caseDetails = startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData = caseDetails.getData();
        // We need to add an 'empty' policy:
        // {
        //   "Organisation": {
        //     "OrganisationID": null,
        //     "OrganisationName": null
        //   },
        //   "OrgPolicyReference": null,
        //   "OrgPolicyCaseAssignedRole": "[APPLICANTSOLICITOR]"
        // }
        final Map<String, Object> organisation = new HashMap<>();
        organisation.put("OrganisationId", null);
        organisation.put("OrganisationName", null);

        final Map<String, Object> policy = new HashMap<>();
        policy.put("Organisation", organisation);
        policy.put("OrgPolicyReference", null);
        policy.put("OrgPolicyCaseAssignedRole", "[APPLICANTSOLICITOR]");

        migratedData.put(
                APPLICANT_ORGANISATION_POLICY,
                policy);

        final Event event = Event.builder()
                .id(startEventResponse.getEventId())
                .summary(EVENT_SUMMARY)
                .description(EVENT_DESCRIPTION)
                .build();

        final CaseDataContent caseDataContent = CaseDataContent.builder()
                .eventToken(startEventResponse.getToken())
                .event(event)
                .data(migratedData)
                .build();

        if (config.isDryRun()) {
            log.info("DTSPB-5005: DRY RUN - returning without submission for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return true;
        }
        // We use the authentication provided in the MigrationEvent to ensure
        // that we don't start events with one set of authentication tokens
        // and then submit them with another.
        final CaseDetails result = coreCaseDataApi.submitEventForCaseWorker(
                migrationEvent.userToken().getBearerToken(),
                migrationEvent.s2sToken().s2sToken(),
                migrationEvent.userToken().userDetails().getId(),
                caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId(),
                caseDetails.getId().toString(),
                true,
                caseDataContent);

        if (result == null) {
            log.error("DTSPB-5005: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5005: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
        return true;
    }

    Set<CaseSummary> getGorCases(
            final UserToken userToken,
            final S2sToken s2sToken) {
        Set<CaseSummary> candidateGorCases = new HashSet<>();
        final JSONObject initialGorQuery = elasticQueries.getGorMigrationQuery(
            config.getQuerySize(),
            Optional.empty());

        log.info("DTSPB-5005 initial query for GoR cases");
        final SearchResult initialGorSearchResult = coreCaseDataApi.searchCases(
                userToken.getBearerToken(),
                s2sToken.s2sToken(),
                GRANT_OF_REPRESENTATION,
                initialGorQuery.toString());
        if (initialGorSearchResult != null && initialGorSearchResult.getTotal() > 0) {
            final List<CaseDetails> initialGorCases = initialGorSearchResult.getCases();
            log.info("DTSPB-5005 initial query found {} GoR cases", initialGorCases.size());

            for (final CaseDetails c : initialGorCases) {
                candidateGorCases.add(new CaseSummary(c.getId(), CaseType.GRANT_OF_REPRESENTATION));
            }
            Long highestCaseRef = initialGorCases.getLast().getId();

            // this feels wasteful if we have fewer than config.querySize results
            boolean keepSearching = true;
            while (keepSearching) {
                final JSONObject trailingGorQuery = elasticQueries.getGorMigrationQuery(
                        config.getQuerySize(),
                        Optional.of(highestCaseRef));

                log.info("DTSPB-5005 searching for trailing GoR cases");
                final SearchResult trailingGorSearchResult = coreCaseDataApi.searchCases(
                        userToken.getBearerToken(),
                        s2sToken.s2sToken(),
                        GRANT_OF_REPRESENTATION,
                        trailingGorQuery.toString());

                if (trailingGorSearchResult != null) {
                    final List<CaseDetails> trailingGorCases = trailingGorSearchResult.getCases();
                    log.info("DTSPB-5005 trailing GoR case search found {} cases", trailingGorCases.size());

                    // should this be .size() < config.querySize ?
                    keepSearching = !trailingGorCases.isEmpty();
                    if (keepSearching) {
                        for (final CaseDetails c : trailingGorCases) {
                            candidateGorCases.add(new CaseSummary(c.getId(), CaseType.GRANT_OF_REPRESENTATION));
                        }
                        highestCaseRef = trailingGorCases.getLast().getId();
                    }
                } else {
                    keepSearching = false;
                    log.info("DTSPB-5005 trailing GoR case search found no cases");
                }
            }
        } else {
            log.info("DTSPB-5005 initial query found no GoR cases");
        }
        // return immutable copy of results
        return Set.copyOf(candidateGorCases);
    }

    Set<CaseSummary> getCaveatCases(
            final UserToken userToken,
            final S2sToken s2sToken) {
        Set<CaseSummary> candidateCaveatCases = new HashSet<>();
        final JSONObject initialCaveatQuery = elasticQueries.getCaveatMigrationQuery(
                config.getQuerySize(),
                Optional.empty());

        log.info("DTSPB-5005 initial query for Caveat cases");
        final SearchResult initialCaveatSearchResult = coreCaseDataApi.searchCases(
                userToken.getBearerToken(),
                s2sToken.s2sToken(),
                CAVEAT,
                initialCaveatQuery.toString());
        if (initialCaveatSearchResult != null && initialCaveatSearchResult.getTotal() > 0) {
            final List<CaseDetails> initialCaveatCases = initialCaveatSearchResult.getCases();
            log.info("DTSPB-5005 initial query found {} Caveat cases", initialCaveatCases.size());

            for (final CaseDetails c : initialCaveatCases) {
                candidateCaveatCases.add(new CaseSummary(c.getId(), CaseType.CAVEAT));
            }
            Long highestCaseRef = initialCaveatCases.getLast().getId();

            // this feels wasteful if we have fewer than config.querySize results
            boolean keepSearching = true;
            while (keepSearching) {
                final JSONObject trailingCaveatQuery = elasticQueries.getCaveatMigrationQuery(
                        config.getQuerySize(),
                        Optional.of(highestCaseRef));

                log.info("DTSPB-5005 searching for trailing Caveat cases");
                final SearchResult trailingCaveatSearchResult = coreCaseDataApi.searchCases(
                        userToken.getBearerToken(),
                        s2sToken.s2sToken(),
                        CAVEAT,
                        trailingCaveatQuery.toString());

                if (trailingCaveatSearchResult != null) {
                    final List<CaseDetails> trailingCaveatCases = trailingCaveatSearchResult.getCases();
                    log.info("DTSPB-5005 trailing Caveat case search found {} cases", trailingCaveatCases.size());

                    // should this be .size() < config.querySize ?
                    keepSearching = !trailingCaveatCases.isEmpty();
                    if (keepSearching) {
                        for (final CaseDetails c : trailingCaveatCases) {
                            candidateCaveatCases.add(new CaseSummary(c.getId(), CaseType.CAVEAT));
                        }
                        highestCaseRef = trailingCaveatCases.getLast().getId();
                    }
                } else {
                    keepSearching = false;
                    log.info("DTSPB-5005 trailing Caveat case search found no cases");
                }
            }
        } else {
            log.info("DTSPB-5005 initial query found no Caveat cases");
        }
        return candidateCaveatCases;
    }

    private record EventDetails(String caseType, String eventId) {}
}
