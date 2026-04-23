package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472;

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
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.PA_ADOPTED_CHILD;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.PA_CHILD;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.PA_RELATIONSHIP_TO_DECEASED;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.SOL_ADOPTED_CHILD;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.SOL_CHILD;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.SOL_RELATIONSHIP_TO_DECEASED;

@Component
@Slf4j
public class Dtspb5472RollbackMigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final CaseEventsApi caseEventsApi;
    private final ElasticSearchHandler elasticSearchHandler;

    private final Dtspb5472Config config;
    private final Dtspb5472ElasticQueries elasticQueries;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String JURISDICTION = "PROBATE";
    static final String PA_ADOPTED_IN = "primaryApplicantAdoptedIn";

    static final String MIGRATION_EVENT = "boHistoryCorrection";

    static final String ROLLBACK_SUMMARY = "DTSPB-5472 - Rollback applicant's relationship to deceased";
    static final String ROLLBACK_DESCRIPTION = "Rollback remove Adopted Child option";

    static final String ROLLBACK_ID = "DTSPB-5472_rollback";

    public Dtspb5472RollbackMigrationHandler(
        final CoreCaseDataApi coreCaseDataApi,
        final CaseEventsApi caseEventsApi,
        final ElasticSearchHandler elasticSearchHandler,
        final Dtspb5472Config config,
        final Dtspb5472ElasticQueries elasticQueries) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.caseEventsApi = Objects.requireNonNull(caseEventsApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);

        this.config = Objects.requireNonNull(config);
        this.elasticQueries = Objects.requireNonNull(elasticQueries);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken) {

        final Set<CaseSummary> gorCandidates = elasticSearchHandler.searchCases(
            ROLLBACK_ID,
            userToken,
            s2sToken,
            CaseType.GRANT_OF_REPRESENTATION,
            fR -> elasticQueries.getGorRollbackQuery(
                    config.getQuerySize(),
                    config.getRollbackDate(),
                    fR));
        final Set<CaseSummary> candidateCases = new HashSet<>(gorCandidates);

        return Set.copyOf(candidateCases);
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

        log.info("DTSPB-5472_rollback start event for {} case {}",
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
            log.error("DTSPB-5472_rollback: No case details present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5472RollbackException(
                    "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5472_rollback: No case data present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5472RollbackException(
                    "No case data present in startEventResponse for " + caseSummary.reference());
        }

        final boolean hasPaAdoptedIn = caseData.containsKey(PA_ADOPTED_IN);
        final boolean hasChild = hasSolChild(caseData) || hasPaChild(caseData);
        if (!hasPaAdoptedIn && !hasChild) {
            log.info("DTSPB-5472_rollback: case {} "
                + "does not have primaryApplicantAdoptedIn or Child so no rollback needed",
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
                .filter(this::findDtspb5472MigrationEvent)
                .toList();

        log.info("DTSPB-5472_rollback: found {} migration events for {} case {}",
                migrationEvents.size(),
                caseSummary.type(),
                caseSummary.reference());
        return !migrationEvents.isEmpty();
    }

    boolean findDtspb5472MigrationEvent(final CaseEventDetail caseEventDetail) {
        final String eventId = caseEventDetail.getId();
        final boolean correctEvent = eventId.equals(MIGRATION_EVENT);
        final String description = caseEventDetail.getDescription();
        final boolean correctDescription = description.equals(Dtspb5472MigrationHandler.MIGRATION_DESCRIPTION);

        return correctEvent && correctDescription;
    }

    @Override
    public boolean migrate(
            final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();

        final CaseDetails caseDetails = startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData = caseDetails.getData();

        // We cannot directly remove the data as part of the event - ccd will pick the value back up from the
        // existing data record
        final JSONObject migrationCallbackMetadataJson = new JSONObject();
        migrationCallbackMetadataJson.put("migrationId", ROLLBACK_ID);
        migratedData.put("migrationCallbackMetadata", migrationCallbackMetadataJson.toString());
        if (hasPaChild(migratedData)) {
            migratedData.put(PA_RELATIONSHIP_TO_DECEASED, PA_ADOPTED_CHILD);
        }
        if (hasSolChild(migratedData)) {
            migratedData.put(SOL_RELATIONSHIP_TO_DECEASED, SOL_ADOPTED_CHILD);
        }

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

        if (config.isDryRun()) {
            log.info("DTSPB-5472_rollback: DRY RUN - returning without submission for {} case {}",
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
            log.error("DTSPB-5472_rollback: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5472_rollback: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());

        return true;
    }

    private record RollbackEventDetails(String caseType, String eventId) {}

    class Dtspb5472RollbackException extends RuntimeException {
        public Dtspb5472RollbackException(final String message) {
            super(message);
        }
    }

    private boolean hasPaChild(Map<String, Object> caseData) {
        return caseData.containsKey(PA_RELATIONSHIP_TO_DECEASED)
            && caseData.get(PA_RELATIONSHIP_TO_DECEASED).equals(PA_CHILD);
    }

    private boolean hasSolChild(Map<String, Object> caseData) {
        return caseData.containsKey(SOL_RELATIONSHIP_TO_DECEASED)
            && caseData.get(SOL_RELATIONSHIP_TO_DECEASED).equals(SOL_CHILD);
    }
}
