package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113;

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
public class Dtspb5113MigrationHandler implements MigrationHandler {
    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;

    private final ReimplConfig commonConfig;
    private final Dtspb5113Config config;
    private final Dtspb5113ElasticQueries elasticQueries;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String JURISDICTION = "PROBATE";
    static final String GRANT_ISSUED_DATE = "grantIssuedDate";


    static final String MIGRATION_SUMMARY = "DTSPB-5113 - Move Dormant case to previous state";
    static final String MIGRATION_DESCRIPTION = "Move Dormant case to previous state";

    public Dtspb5113MigrationHandler(
            final CoreCaseDataApi coreCaseDataApi,
            final ElasticSearchHandler elasticSearchHandler,
            final ReimplConfig commonConfig,
            final Dtspb5113Config config,
            final Dtspb5113ElasticQueries elasticQueries) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);
        this.commonConfig = Objects.requireNonNull(commonConfig);
        this.config = Objects.requireNonNull(config);
        this.elasticQueries = Objects.requireNonNull(elasticQueries);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken) {

        final Set<CaseSummary> gorCandidates = elasticSearchHandler.searchCases(
                "DTSPB-5113",
                userToken,
                s2sToken,
                CaseType.GRANT_OF_REPRESENTATION,
                fR -> elasticQueries.getGorMigrationQuery(commonConfig.getQuerySize(), fR));
        final Set<CaseSummary> candidateCases = new HashSet<>(gorCandidates);

        return Set.copyOf(candidateCases);
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

        log.info("DTSPB-5113 start event for {} case {}",
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
            log.error("DTSPB-5113: No case details present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new Dtspb5113MigrationException(
                    "No case details present in startEventResponse for " + caseSummary.reference());
        }

        final Map<String, Object> caseData = caseDetails.getData();
        if (caseData == null) {
            log.error("DTSPB-5113: No case data present in startEventResponse for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            throw new Dtspb5113MigrationException(
                    "No case data present in startEventResponse for " + caseSummary.reference());
        }

        final boolean hasGrantIssuedDate = caseData.containsKey(GRANT_ISSUED_DATE);
        if (!hasGrantIssuedDate) {
            log.info("DTSPB-5113: case {} don't have grant issued date so no migration needed",
                    caseSummary.reference());
        }
        return hasGrantIssuedDate;
    }

    @Override
    public boolean migrate(
            final MigrationEvent migrationEvent) {
        final CaseSummary caseSummary = migrationEvent.caseSummary();
        final StartEventResponse startEventResponse = migrationEvent.startEventResponse();

        final CaseDetails caseDetails = startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData = caseDetails.getData();

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
            log.info("DTSPB-5113: DRY RUN - returning without submission for {} case {}",
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
            log.error("DTSPB-5113: event submission returned null for {} case {}",
                    caseSummary.type(),
                    caseSummary.reference());
            return false;
        }
        log.info("DTSPB-5113: event submission complete for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
        return true;
    }

    private record MigrationEventDetails(String caseType, String eventId) {}

    class Dtspb5113MigrationException extends RuntimeException {
        public Dtspb5113MigrationException(final String message) {
            super(message);
        }
    }
}
