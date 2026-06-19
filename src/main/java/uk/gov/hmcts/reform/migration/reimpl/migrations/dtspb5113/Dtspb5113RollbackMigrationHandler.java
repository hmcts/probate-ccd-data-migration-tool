package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CaseEventsApi;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.CaseEventDetail;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class Dtspb5113RollbackMigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final CaseEventsApi caseEventsApi;
    private final ElasticSearchHandler elasticSearchHandler;
    private final ReimplConfig commonConfig;
    private final Dtspb5113Config config;
    private final Dtspb5113ElasticQueries elasticQueries;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String JURISDICTION = "PROBATE";
    // Check that a previous migration event has happened
    static final String MIGRATION_EVENT = "boHistoryCorrection";
    static final String GRANT_ISSUED_DATE = "grantIssuedDate";

    static final String ROLLBACK_SUMMARY = "DTSPB-5113 - Rollback to Dormant state";
    static final String ROLLBACK_DESCRIPTION = "Rollback to Dormant state";

    static final String ROLLBACK_ID = "DTSPB-5113_rollback";

    public Dtspb5113RollbackMigrationHandler(
        final CoreCaseDataApi coreCaseDataApi,
        final CaseEventsApi caseEventsApi,
        final ElasticSearchHandler elasticSearchHandler,
        final ReimplConfig commonConfig,
        final Dtspb5113Config config,
        final Dtspb5113ElasticQueries elasticQueries) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.caseEventsApi = Objects.requireNonNull(caseEventsApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);
        this.commonConfig = Objects.requireNonNull(commonConfig);
        this.config = Objects.requireNonNull(config);
        this.elasticQueries = Objects.requireNonNull(elasticQueries);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken) {

        final Set<CaseSummary> gorCandidates = commonConfig.getCaseReferences()
            .orElseThrow(() -> new IllegalStateException(
                "caseReferences must be configured for this rollback migration"));
        return Set.copyOf(gorCandidates);
    }

    @Override
    public MigrationEvent startEventForCase(
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken) {

        final RollbackEventDetails eventDetails = new RollbackEventDetails(
                GRANT_OF_REPRESENTATION,
                "boCorrection");

        final UserDetails userDetails = userToken.userDetails();

        log.info("DTSPB-5113_rollback start event for {} case {}",
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
        final UserToken userToken = migrationEvent.userToken();
        final S2sToken s2sToken = migrationEvent.s2sToken();
        if (caseDetails == null) {
            log.error("DTSPB-5113_rollback: No case details present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5113RollbackException(
                    "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5113_rollback: No case data present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5113RollbackException(
                    "No case data present in startEventResponse for " + caseSummary.reference());
        }

        final boolean hasGrantIssuedDate = caseData.containsKey(GRANT_ISSUED_DATE);
        if (!hasGrantIssuedDate) {
            log.info("DTSPB-5113_rollback: case {} "
                + "does not have grantIssuedDate so no rollback needed",
                caseSummary.reference());
            return false;
        }

        final List<CaseEventDetail> caseEvents = caseEventsApi.findEventDetailsForCase(
                userToken.getBearerToken(),
                s2sToken.s2sToken(),
                userToken.userDetails().getId(),
                caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId(),
                caseDetails.getId().toString());

        final List<CaseEventDetail> migrationEvents = caseEvents.stream()
                .filter(this::findDtspb5113MigrationEvent)
                .toList();

        log.info("DTSPB-5113_rollback: found {} migration events for {} case {}",
                migrationEvents.size(),
                caseSummary.type(),
                caseSummary.reference());
        return !migrationEvents.isEmpty();
    }

    boolean findDtspb5113MigrationEvent(final CaseEventDetail caseEventDetail) {
        final String eventId = caseEventDetail.getId();
        final boolean correctEvent = eventId.equals(MIGRATION_EVENT);
        final String description = caseEventDetail.getDescription();
        final boolean correctDescription = description.equals(Dtspb5113MigrationHandler.MIGRATION_DESCRIPTION);

        return correctEvent && correctDescription;
    }

    @Override
    public boolean migrate(
            final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();

        final CaseDetails caseDetails = startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData = caseDetails.getData();

        // We cannot directly update state as part of the event - ccd will pick the value back up from the
        // existing data record
        final JSONObject migrationCallbackMetadataJson = new JSONObject();
        migrationCallbackMetadataJson.put("migrationId", ROLLBACK_ID);
        migratedData.put("migrationCallbackMetadata", migrationCallbackMetadataJson.toString());

        final Event event = Event.builder()
                .id(startEventResponse.getEventId())
                .summary(ROLLBACK_SUMMARY)
                .description(ROLLBACK_DESCRIPTION)
                .build();

        final CaseDataContent caseDataContent = CaseDataContent.builder()
                .eventToken(startEventResponse.getToken())
                .event(event)
                .data(migratedData)
                .build();

        if (commonConfig.isDryRun()) {
            log.info("DTSPB-5113_rollback: DRY RUN - returning without submission for {} case {}",
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
            log.error("DTSPB-5113_rollback: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5113_rollback: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());

        return true;
    }

    private record RollbackEventDetails(String caseType, String eventId) {}

    class Dtspb5113RollbackException extends RuntimeException {
        public Dtspb5113RollbackException(final String message) {
            super(message);
        }
    }
}
