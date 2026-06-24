package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5539;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.Collections.singletonMap;

@Slf4j
@Component
public class Dtspb5539MigrationHandler implements MigrationHandler {

    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb5539ElasticQueries elasticQueries;
    private final Dtspb5539Config config;
    static final String MIGRATION_SUMMARY = "DTSPB-5339 - Add metadata for Global Search";
    static final String MIGRATION_DESCRIPTION = "Add metadata for Global Search";
    static final String ROLLBACK_ID = "DTSPB-5539";
    private final ReimplConfig commonConfig;
    private static final String SUPPLEMENTARY_FIELD = "supplementary_data_updates";
    private static final String SET_OPERATION = "$set";
    private static final String SERVICE_ID_FIELD = "HMCTSServiceId";



    public Dtspb5539MigrationHandler(
        CoreCaseDataApi coreCaseDataApi,
        final ElasticSearchHandler elasticSearchHandler,
        final Dtspb5539ElasticQueries elasticQueries,
        final Dtspb5539Config config,
        final ReimplConfig commonConfig) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.elasticSearchHandler = elasticSearchHandler;
        this.elasticQueries = elasticQueries;
        this.config = config;
        this.commonConfig = commonConfig;
    }

    @Override
    public Set<CaseSummary> getCandidateCases(UserToken userToken, S2sToken s2sToken) {
        final Set<CaseSummary> candidateCases = new HashSet<>();
        for (CaseType caseType : config.getCaseTypes()) {
            log.info("Starting candidate case search for case type: {}",
                caseType);
            final Set<CaseSummary> candidates = elasticSearchHandler.searchCases(
                "DTSPB-5539",
                userToken,
                s2sToken,
                caseType,
                fR -> elasticQueries.getMigrationQuery(commonConfig.getQuerySize(), fR));
            candidateCases.addAll(candidates);


            log.info("Found {} candidate cases", candidates.size());
            candidates.forEach(caseSummary ->
                log.info("Candidate case: {}", caseSummary)
            );

        }
        return Set.copyOf(candidateCases);

    }

    @Override
    public MigrationEvent startEventForCase(CaseSummary caseSummary, UserToken userToken, S2sToken s2sToken) {
        final MigrationEventDetails eventDetails = new Dtspb5539MigrationHandler.MigrationEventDetails(
            caseSummary.type().getCcdValue(),
            "boHistoryCorrection"
        );

        final UserDetails userDetails = userToken.userDetails();
        log.info("Calling BOHistoryCorrection event");

        final StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
            userToken.getBearerToken(),
            s2sToken.s2sToken(),
            userDetails.getId(),
            "PROBATE",
            eventDetails.caseType(),
            caseSummary.reference().toString(),
            eventDetails.eventId());
        log.info("StartEventResponse:  {} ", startEventResponse);

        return new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken);
    }

    @Override
    public boolean shouldMigrateCase(MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final CaseDetails caseDetails = migrationEvent.startEventResponse().getCaseDetails();

        if (caseDetails == null) {
            log.error("DTSPB-5539: No case details present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());

            throw new Dtspb5539MigrationException(
                "No case details present in startEventResponse for " + caseSummary.reference());
        }
        return true;
    }

    @Override
    public boolean migrate(MigrationEvent migrationEvent) {

        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();
        final CaseDetails caseDetails = startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData = caseDetails.getData();

        Map<String, Map<String, Map<String, Object>>> supplementaryDataUpdates = new HashMap<>();
        supplementaryDataUpdates.put(SUPPLEMENTARY_FIELD,
            singletonMap(SET_OPERATION, singletonMap(SERVICE_ID_FIELD, config.getHmctsId())));

        // We cannot directly remove the data as part of the event - ccd will pick the value back up from the
        // existing data record
        final JSONObject migrationCallbackMetadataJson = new JSONObject();
        migrationCallbackMetadataJson.put("migrationId", ROLLBACK_ID);
        migratedData.put("migrationCallbackMetadata", migrationCallbackMetadataJson.toString());

        //Nothing will be migrated when dry run is true
        if (commonConfig.isDryRun()) {
            log.info("DTSPB-5539: DRY RUN - returning without submission for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            return true;
        }

        final Event event = Event.builder()
            .id(startEventResponse.getEventId())
            .summary(MIGRATION_SUMMARY)
            .description(MIGRATION_DESCRIPTION)
            .build();

        log.info("Event object = {}", event);
        log.info("Event id = {}", event.getId());
        log.info("Start event id = {}", startEventResponse.getEventId());

        final CaseDataContent caseDataContent = CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(event)
            .data(migratedData)
            .build();

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
            log.error("DTSPB-5539: event submission returned null for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            return false;
        }
        try {
            coreCaseDataApi.submitSupplementaryData(migrationEvent.userToken().getBearerToken(),
                migrationEvent.s2sToken().s2sToken(),
                caseDetails.getId().toString(),
                supplementaryDataUpdates);

            log.info(
                "DTSPB-5539: Global Search supplementary data added for {} case {} (caseId={})",
                caseSummary.type(),
                caseSummary.reference(),
                caseDetails.getId()
            );
        } catch (FeignException ex) {
            log.error(
                "DTSPB-5539: Failed to update supplementary data for {} case {} "
                    + "(caseId={}). Status={}, Response={}, Payload={}",
                caseSummary.type(),
                caseSummary.reference(),
                caseDetails.getId(),
                ex.status(),
                ex.contentUTF8(),
                supplementaryDataUpdates,
                ex
            );
            return false;
        }
        return true;
    }

    private record MigrationEventDetails(String caseType, String eventId) {}

    class Dtspb5539MigrationException extends RuntimeException {
        public Dtspb5539MigrationException(final String message) {
            super(message);
        }
    }
}
