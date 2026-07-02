package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class Dtspb5130MigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;

    private final ReimplConfig reimplConfig;
    private final Dtspb5130ElasticQueries elasticQueries;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String JURISDICTION = "PROBATE";
    static final String YES = "Yes";
    static final String NO = "No";

    static final String MIGRATION_SUMMARY = "DTSPB-5130 - Migrating Handoff Reasons";
    static final String MIGRATION_DESCRIPTION = "Mark Evidence Handled Yes For Closed Cases";

    public Dtspb5130MigrationHandler(
            final CoreCaseDataApi coreCaseDataApi,
            final ElasticSearchHandler elasticSearchHandler,
            final ReimplConfig reimplConfig,
            final Dtspb5130ElasticQueries elasticQueries) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);

        this.reimplConfig = Objects.requireNonNull(reimplConfig);
        this.elasticQueries = Objects.requireNonNull(elasticQueries);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken) {
        final Set<CaseSummary> candidateCases = new HashSet<>();

        final Set<CaseSummary> gorCandidates = elasticSearchHandler.searchCases(
                "DTSPB-5130",
                userToken,
                s2sToken,
                CaseType.GRANT_OF_REPRESENTATION,
                fR -> elasticQueries.getGorMigrationQuery(reimplConfig.getQuerySize(), fR));
        candidateCases.addAll(gorCandidates);


        return Set.copyOf(candidateCases);
    }

    @Override
    public MigrationEvent startEventForCase(
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken) {

        final MigrationEventDetails eventDetails = new MigrationEventDetails(
                GRANT_OF_REPRESENTATION,
                "boHistoryCorrection"
        );

        final UserDetails userDetails = userToken.userDetails();

        log.info("DTSPB-5130 start event for {} case {}",
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
            log.error("DTSPB-5130: No case details present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new Dtspb5130MigrationException(
                    "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5130: No case data present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new Dtspb5130MigrationException(
                    "No case data present in startEventResponse for " + caseSummary.reference());
        }

        Object evidenceHandledObj = caseData.get("evidenceHandled");
        if (!(evidenceHandledObj instanceof String)) {
            log.info("DTSPB-5130: evidence handled is not a string");
            return false;
        }
        @SuppressWarnings("unchecked")
        String evidenceHandled = (String) evidenceHandledObj;

        return evidenceHandled.equalsIgnoreCase(NO);
    }

    @Override
    public boolean migrate(final MigrationEvent migrationEvent) {

        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();
        final CaseDetails caseDetails = startEventResponse.getCaseDetails();
        final Map<String, Object> migratedData = caseDetails.getData();

        Object evidenceHandledObj = migratedData.get("evidenceHandled");
        if (!(evidenceHandledObj instanceof String)) {
            log.info("DTSPB-5130: evidence handled is not a string");
            return false;
        }

        @SuppressWarnings("unchecked")
        String evidenceHandled = (String) evidenceHandledObj;

        if (evidenceHandled.equals(NO)) {
            migratedData.put("evidenceHandled", YES);
        }

        final Event event = Event.builder()
                .id(startEventResponse.getEventId())
                .summary(MIGRATION_SUMMARY)
                .description(MIGRATION_DESCRIPTION)
                .build();

        final CaseDataContent caseDataContent = CaseDataContent.builder()
                .eventToken(startEventResponse.getToken())
                .event(event)
                .data(migratedData)
                .build();

        if (reimplConfig.isDryRun()) {
            log.info("DTSPB-5130: DRY RUN - returning without submission for {} case {}",
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
            log.error("DTSPB-5130: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5130: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
        return true;
    }

    private record MigrationEventDetails(String caseType, String eventId) {}

    class Dtspb5130MigrationException extends RuntimeException {
        public Dtspb5130MigrationException(final String message) {
            super(message);
        }
    }
}
