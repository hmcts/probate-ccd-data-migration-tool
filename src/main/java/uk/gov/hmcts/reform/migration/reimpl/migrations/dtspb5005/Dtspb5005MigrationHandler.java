package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5005;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class Dtspb5005MigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;

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
            final ElasticSearchHandler elasticSearchHandler,
            final Dtspb5005Config config,
            final Dtspb5005ElasticQueries elasticQueries) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);

        this.config = Objects.requireNonNull(config);
        this.elasticQueries = Objects.requireNonNull(elasticQueries);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken) {
        final Set<CaseSummary> candidateCases = new HashSet<>();

        final Set<CaseSummary> gorCandidates = elasticSearchHandler.searchCases(
                "DTSPB-5005",
                userToken,
                s2sToken,
                CaseType.GRANT_OF_REPRESENTATION,
                fR -> elasticQueries.getGorMigrationQuery(config.getQuerySize(), fR));
        candidateCases.addAll(gorCandidates);

        final Set<CaseSummary> caveatCandidates = elasticSearchHandler.searchCases(
                "DTSPB-5005",
                userToken,
                s2sToken,
                CaseType.CAVEAT,
                fR -> elasticQueries.getCaveatMigrationQuery(config.getQuerySize(), fR));
        candidateCases.addAll(caveatCandidates);

        return Set.copyOf(candidateCases);
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
            throw new Dtspb5005MigrationException(
                    "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5005: No case data present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new Dtspb5005MigrationException(
                    "No case data present in startEventResponse for " + caseSummary.reference());
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

    private record EventDetails(String caseType, String eventId) {}

    private class Dtspb5005MigrationException extends RuntimeException {
        public Dtspb5005MigrationException(final String message) {
            super(message);
        }
    }
}
