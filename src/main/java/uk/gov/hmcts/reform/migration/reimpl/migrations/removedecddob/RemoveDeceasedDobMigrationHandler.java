package uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob;

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

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class RemoveDeceasedDobMigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;

    private final ReimplConfig commonConfig;
    private final RemoveDeceasedDobConfig config;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String JURISDICTION = "PROBATE";

    static final String MIGRATION_ID = "clear_deceased_date_of_birth";

    static final String MIGRATION_SUMMARY = "remove_deceased_dob - Remove deceased's date of birth";
    static final String MIGRATION_DESCRIPTION = "Remove deceased's date of birth";

    public RemoveDeceasedDobMigrationHandler(
            final CoreCaseDataApi coreCaseDataApi,
            final ElasticSearchHandler elasticSearchHandler,
            final ReimplConfig commonConfig,
            final RemoveDeceasedDobConfig config) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);
        this.commonConfig = Objects.requireNonNull(commonConfig);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken) {

        final Set<CaseSummary> casesToHandle = config.getCaseRefs().orElseThrow(
            () -> new IllegalStateException("No CaseRefs configured"));

        return Set.copyOf(casesToHandle);
    }

    @Override
    public MigrationEvent startEventForCase(
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken) {

        final MigrationEventDetails eventDetails =  new MigrationEventDetails(
            GRANT_OF_REPRESENTATION,
            "boHistoryCorrection");

        final UserDetails userDetails = userToken.userDetails();

        log.info("remove_deceased_dob: start event for {} case {}",
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
        return true;
    }

    @Override
    public boolean migrate(
            final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();

        final CaseDetails caseDetails = startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData = caseDetails.getData();

        // We cannot directly remove the date of birth as part of the event - ccd will pick the value back up from the
        // existing data record
        final JSONObject migrationCallbackMetadataJson = new JSONObject();
        migrationCallbackMetadataJson.put("migrationId", MIGRATION_ID);
        migratedData.put("migrationCallbackMetadata", migrationCallbackMetadataJson.toString());

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

        if (commonConfig.isDryRun()) {
            log.info("remove_deceased_dob: DRY RUN - returning without submission for {} case {}",
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
            log.error("remove_deceased_dob: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("remove_deceased_dob: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
        return true;
    }

    private record MigrationEventDetails(String caseType, String eventId) {}
}
