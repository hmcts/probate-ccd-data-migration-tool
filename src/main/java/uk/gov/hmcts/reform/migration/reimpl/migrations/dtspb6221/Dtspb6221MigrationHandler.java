package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb6221;

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

import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class Dtspb6221MigrationHandler implements MigrationHandler {

    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;
    private final ReimplConfig reimplConfig;
    private final Dtspb6221ElasticQueries elasticQueries;

    static final String GRANT_OF_REPRESENTATION = "GrantOfRepresentation";
    static final String JURISDICTION = "PROBATE";
    static final String CTSC = "ctsc";

    static final Set<String> LOCATIONS_TO_MIGRATE = Set.of(
        "London",
        "Manchester",
        "Birmingham",
        "Bristol"
    );

    static final Set<String> EXCLUDED_STATES = Set.of(
        "BOCaseMatchingReissue",
        "BOCaseStoppedReissue",
        "BOExaminingReissue",
        "BOGrantIssued",
        "BOGrantIssuedRegistrarEscalation",
        "BOPostGrantIssued"
    );

    static final String MIGRATION_SUMMARY =
        "DTSPB-6221 - Update Registry Locations";

    static final String MIGRATION_DESCRIPTION =
        "Change registryLocation to ctsc";

    public Dtspb6221MigrationHandler(
        final CoreCaseDataApi coreCaseDataApi,
        final ElasticSearchHandler elasticSearchHandler,
        final ReimplConfig reimplConfig,
        final Dtspb6221ElasticQueries elasticQueries
    ) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.elasticSearchHandler = Objects.requireNonNull(elasticSearchHandler);
        this.reimplConfig = Objects.requireNonNull(reimplConfig);
        this.elasticQueries = Objects.requireNonNull(elasticQueries);
    }

    @Override
    public Set<CaseSummary> getCandidateCases(
        final UserToken userToken,
        final S2sToken s2sToken
    ) {
        return elasticSearchHandler.searchCases(
            "DTSPB-6221",
            userToken,
            s2sToken,
            CaseType.GRANT_OF_REPRESENTATION,
            elasticQueries::getMigrationQuery
        );
    }

    @Override
    public MigrationEvent startEventForCase(
        final CaseSummary caseSummary,
        final UserToken userToken,
        final S2sToken s2sToken
    ) {

        final UserDetails userDetails = userToken.userDetails();

        log.info(
            "DTSPB-6221 start event for case {}",
            caseSummary.reference()
        );

        final StartEventResponse startEventResponse =
            coreCaseDataApi.startEventForCaseWorker(
                userToken.getBearerToken(),
                s2sToken.s2sToken(),
                userDetails.getId(),
                JURISDICTION,
                GRANT_OF_REPRESENTATION,
                caseSummary.reference().toString(),
                "boHistoryCorrection"
            );

        return new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken
        );
    }

    @Override
    public boolean shouldMigrateCase(
        final MigrationEvent migrationEvent
    ) {

        final CaseDetails caseDetails =
            migrationEvent.startEventResponse().getCaseDetails();
        final CaseSummary caseSummary = migrationEvent.caseSummary();

        if (caseDetails == null) {
            log.error("DTSPB-6221: No case details present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            return false;
        }

        final Map<String, Object> caseData =
            caseDetails.getData();

        if (caseData == null) {
            log.error("DTSPB-6221: No case data present in startEventResponse for {} case {}",
                caseSummary.type(),
                caseSummary.reference());
            return false;
        }

        Object registryLocationObj =
            caseData.get("registryLocation");

        if (!(registryLocationObj instanceof String registryLocation)) {
            log.info("DTSPB-6221: case {} does not have registryLocation so no migration needed",
                caseSummary.reference());
            return false;
        }

        String state = caseDetails.getState();

        if (state == null) {
            return false;
        }

        return LOCATIONS_TO_MIGRATE.contains(registryLocation)
            && !EXCLUDED_STATES.contains(state);
    }

    @Override
    public boolean migrate(
        final MigrationEvent migrationEvent
    ) {

        final CaseSummary caseSummary =
            migrationEvent.caseSummary();

        final StartEventResponse startEventResponse =
            migrationEvent.startEventResponse();

        final CaseDetails caseDetails =
            startEventResponse.getCaseDetails();

        final Map<String, Object> migratedData =
            caseDetails.getData();

        Object registryLocationObj =
            migratedData.get("registryLocation");

        if (!(registryLocationObj instanceof String registryLocation)) {
            return false;
        }

        if (LOCATIONS_TO_MIGRATE.contains(registryLocation)) {
            migratedData.put("registryLocation", CTSC);
        }

        final String migrationDescription =
            MIGRATION_DESCRIPTION + "?" + registryLocation;

        final Event event = Event.builder()
            .id(startEventResponse.getEventId())
            .summary(MIGRATION_SUMMARY)
            .description(migrationDescription)
            .build();

        final CaseDataContent caseDataContent =
            CaseDataContent.builder()
                .eventToken(startEventResponse.getToken())
                .event(event)
                .data(migratedData)
                .build();

        if (reimplConfig.isDryRun()) {
            log.info(
                "DTSPB-6221: DRY RUN - skipping submission for case {}",
                caseSummary.reference()
            );
            return true;
        }

        final CaseDetails result =
            coreCaseDataApi.submitEventForCaseWorker(
                migrationEvent.userToken().getBearerToken(),
                migrationEvent.s2sToken().s2sToken(),
                migrationEvent.userToken().userDetails().getId(),
                caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId(),
                caseDetails.getId().toString(),
                true,
                caseDataContent
            );

        return result != null;
    }
}
