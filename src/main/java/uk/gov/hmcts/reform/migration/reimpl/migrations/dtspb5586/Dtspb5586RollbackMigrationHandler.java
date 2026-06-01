package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
public class Dtspb5586RollbackMigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final CaseEventsApi caseEventsApi;
    private final ElasticSearchHandler elasticSearchHandler;

    private final ReimplConfig reimplConfig;
    private final Dtspb5586Config config;
    private final Dtspb5586ElasticQueries elasticQueries;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String JURISDICTION = "PROBATE";

    static final String MIGRATION_EVENT = "boHistoryCorrection";

    static final String ROLLBACK_SUMMARY = "DTSPB-5586 - Rollback adding metadata for Notice of Change";
    static final String ROLLBACK_DESCRIPTION = "Rollback adding metadata for Notice of Change";

    static final String ROLLBACK_ID = "DTSPB-5586_rollback";

    public Dtspb5586RollbackMigrationHandler(
        final CoreCaseDataApi coreCaseDataApi,
        final CaseEventsApi caseEventsApi,
        final ElasticSearchHandler elasticSearchHandler,
        final ReimplConfig reimplConfig,
        final Dtspb5586Config config,
        final Dtspb5586ElasticQueries elasticQueries) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.caseEventsApi = Objects.requireNonNull(caseEventsApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);

        this.reimplConfig = Objects.requireNonNull(reimplConfig);
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
                    reimplConfig.getQuerySize(),
                    config.getRollbackDate(),
                    fR));
        candidateCases.addAll(gorCandidates);

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
            case CAVEAT -> throw new Dtspb5586RollbackException("Unexpected CAVEAT");
        };

        final UserDetails userDetails = userToken.userDetails();

        log.info("DTSPB-5586_rollback start event for {} case {}",
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
            log.error("DTSPB-5586_rollback: No case details present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5586RollbackException(
                    "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5586_rollback: No case data present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            throw new Dtspb5586RollbackException(
                    "No case data present in startEventResponse for " + caseSummary.reference());
        }

        final List<CaseEventDetail> caseEvents = caseEventsApi.findEventDetailsForCase(
                userToken.getBearerToken(),
                s2sToken.s2sToken(),
                userToken.userDetails().getId(),
                caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId(),
                caseDetails.getId().toString());

        final List<CaseEventDetail> migrationEvents = caseEvents.stream()
                .filter(this::findDtspb5586MigrationEvent)
                .toList();

        log.info("DTSPB-5586_rollback: found {} migration events for {} case {}",
                migrationEvents.size(),
                caseSummary.type(),
                caseSummary.reference());
        return !migrationEvents.isEmpty();
    }

    boolean findDtspb5586MigrationEvent(final CaseEventDetail caseEventDetail) {
        final String eventId = caseEventDetail.getId();
        final boolean correctEvent = eventId.equals(MIGRATION_EVENT);
        final boolean correctDescription =
            Optional.ofNullable(caseEventDetail.getDescription())
                .map(description -> description.contains("DTSPB-5586"))
                .orElse(false);

        return correctEvent && correctDescription;
    }

    @Override
    public boolean migrate(
            final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();
        final CaseDetails caseDetails = startEventResponse.getCaseDetails();
        final Map<String, Object> migratedData = caseDetails.getData();
        final UserToken userToken = migrationEvent.userToken();
        final S2sToken s2sToken = migrationEvent.s2sToken();

        final List<CaseEventDetail> caseEvents = caseEventsApi.findEventDetailsForCase(
            userToken.getBearerToken(),
            s2sToken.s2sToken(),
            userToken.userDetails().getId(),
            caseDetails.getJurisdiction(),
            caseDetails.getCaseTypeId(),
            caseDetails.getId().toString());


        final List<CaseEventDetail> migrationEvents = caseEvents.stream()
            .filter(this::findDtspb5586MigrationEvent)
            .toList();

        if (migrationEvents.isEmpty()) {
            throw new Dtspb5586RollbackException("No migration events found for " + caseSummary.reference());
        }

        CaseEventDetail migrationEventDetail = migrationEvents.get(0);
        String description = migrationEventDetail.getDescription();
        if (description == null || !description.contains("?")) {
            throw new Dtspb5586RollbackException(
                "Migration event description is invalid for " + caseSummary.reference()
            );
        }

        String[] parts = description.split("\\?", 2);
        String handOffReasonsStr = parts[1];

        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, Object>> handOffReasonsToRestore;
        try {
            handOffReasonsToRestore = objectMapper.readValue(
                handOffReasonsStr,
                new TypeReference<>() {}
            );
        } catch (Exception e) {
            throw new Dtspb5586RollbackException("Failed to parse migration handoff reasons");
        }

        if (!handOffReasonsToRestore.isEmpty()) {
            migratedData.put("caseHandedOffToLegacySite", "Yes");
        } else {
            migratedData.put("caseHandedOffToLegacySite", "No");
        }
        migratedData.put("boHandoffReasonList", handOffReasonsToRestore);

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

        if (reimplConfig.isDryRun()) {
            log.info("DTSPB-5586_rollback: DRY RUN - returning without submission for {} case {}",
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
            log.error("DTSPB-5586_rollback: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5586_rollback: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());

        return true;
    }

    private record RollbackEventDetails(String caseType, String eventId) {}

    class Dtspb5586RollbackException extends RuntimeException {
        public Dtspb5586RollbackException(final String message) {
            super(message);
        }
    }
}
