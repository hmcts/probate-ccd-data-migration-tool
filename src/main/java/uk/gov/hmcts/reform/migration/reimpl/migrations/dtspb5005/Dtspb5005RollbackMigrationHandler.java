package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5005;

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

@Component
@Slf4j
public class Dtspb5005RollbackMigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final CaseEventsApi caseEventsApi;
    private final ElasticSearchHandler elasticSearchHandler;

    private final Dtspb5005Config config;
    private final Dtspb5005ElasticQueries elasticQueries;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String CAVEAT = "Caveat";
    static final String JURISDICTION = "PROBATE";
    static final String APPLICANT_ORGANISATION_POLICY = "applicantOrganisationPolicy";

    static final String MIGRATION_EVENT = "boHistoryCorrection";

    static final String ROLLBACK_SUMMARY = "DTSPB-5005 - Rollback adding metadata for Notice of Change";
    static final String ROLLBACK_DESCRIPTION = "Rollback adding metadata for Notice of Change";

    static final String ROLLBACK_ID = "DTSPB-5005_rollback";

    public Dtspb5005RollbackMigrationHandler(
        final CoreCaseDataApi coreCaseDataApi,
        final CaseEventsApi caseEventsApi,
        final ElasticSearchHandler elasticSearchHandler,
        final Dtspb5005Config config,
        final Dtspb5005ElasticQueries elasticQueries) {
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
        final Set<CaseSummary> candidateCases = new HashSet<>();

        final Set<CaseSummary> gorCandidates = elasticSearchHandler.searchCases(
            ROLLBACK_ID,
            userToken,
            s2sToken,
            CaseType.GRANT_OF_REPRESENTATION,
            fR -> elasticQueries.getGorRollbackQuery(
                    config.getQuerySize(),
                    config.getRollbackDate(),
                    fR));
        candidateCases.addAll(gorCandidates);

        final Set<CaseSummary> caveatCandidates = elasticSearchHandler.searchCases(
            ROLLBACK_ID,
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            fR -> elasticQueries.getCaveatRollbackQuery(
                    config.getQuerySize(),
                    config.getRollbackDate(),
                    fR));
        candidateCases.addAll(caveatCandidates);

        return Set.copyOf(candidateCases);
    }

    @Override
    public MigrationEvent startEventForCase(
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken) {

        final RollbackEventDetails eventDetails = switch (caseSummary.type()) {
            case GRANT_OF_REPRESENTATION -> new RollbackEventDetails(
                GRANT_OF_REPRESENTATION,
                "boCorrection");
            case CAVEAT -> new RollbackEventDetails(
                CAVEAT,
                "boCorrection");
        };

        final UserDetails userDetails = userToken.userDetails();

        log.info("DTSPB-5005_rollback start event for {} case {}",
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
            log.error("DTSPB-5005_rollback: No case details present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5005RollbackException(
                    "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5005_rollback: No case data present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5005RollbackException(
                    "No case data present in startEventResponse for " + caseSummary.reference());
        }

        final boolean hasApplOrgPolicy = caseData.containsKey(APPLICANT_ORGANISATION_POLICY);
        if (!hasApplOrgPolicy) {
            log.info("DTSPB-5005_rollback: {} case {} does not have applicantOrganisationPolicy so no rollback needed",
                caseSummary.type(),
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
                .filter(this::findDtspb5005MigrationEvent)
                .toList();

        log.info("DTSPB-5005_rollback: found {} migration events for {} case {}",
                migrationEvents.size(),
                caseSummary.type(),
                caseSummary.reference());
        return !migrationEvents.isEmpty();
    }

    boolean findDtspb5005MigrationEvent(final CaseEventDetail caseEventDetail) {
        final String eventId = caseEventDetail.getId();
        final boolean correctEvent = eventId.equals(MIGRATION_EVENT);
        final String description = caseEventDetail.getDescription();
        final boolean correctDescription = description.equals(Dtspb5005MigrationHandler.MIGRATION_DESCRIPTION);

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
            log.info("DTSPB-5005_rollback: DRY RUN - returning without submission for {} case {}",
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
            log.error("DTSPB-5005_rollback: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5005_rollback: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());

        return true;
    }

    private record RollbackEventDetails(String caseType, String eventId) {}

    class Dtspb5005RollbackException extends RuntimeException {
        public Dtspb5005RollbackException(final String message) {
            super(message);
        }
    }
}
