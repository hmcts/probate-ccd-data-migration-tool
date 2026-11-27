package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb6221;

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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
public class Dtspb6221RollbackMigrationHandler
    implements MigrationHandler {

    private final CoreCaseDataApi coreCaseDataApi;
    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb6221ElasticQueries elasticQueries;
    private final ReimplConfig reimplConfig;
    private final CaseEventsApi caseEventsApi;

    static final String MIGRATION_EVENT = "boHistoryCorrection";

    static final String ROLLBACK_SUMMARY =
        "DTSPB-6221 Rollback";

    static final String ROLLBACK_DESCRIPTION =
        "Rollback DTSPB-6221";

    public Dtspb6221RollbackMigrationHandler(
        final CoreCaseDataApi coreCaseDataApi,
        final ElasticSearchHandler elasticSearchHandler,
        final Dtspb6221ElasticQueries elasticQueries,
        final ReimplConfig reimplConfig,
        final CaseEventsApi caseEventsApi
    ) {
        this.coreCaseDataApi = Objects.requireNonNull(coreCaseDataApi);
        this.elasticSearchHandler =
            Objects.requireNonNull(elasticSearchHandler);
        this.elasticQueries =
            Objects.requireNonNull(elasticQueries);
        this.caseEventsApi =
            Objects.requireNonNull(caseEventsApi);
        this.reimplConfig =
            Objects.requireNonNull(reimplConfig);
    }

    boolean findDtspb6221MigrationEvent(
        final CaseEventDetail caseEventDetail
    ) {

        final boolean correctEvent =
            MIGRATION_EVENT.equals(
                caseEventDetail.getId()
            );

        final boolean correctDescription =
            Optional.ofNullable(caseEventDetail.getDescription())
                .map(description ->
                    description.startsWith(
                        Dtspb6221MigrationHandler.MIGRATION_DESCRIPTION
                    )
                )
                .orElse(false);

        return correctEvent
            && correctDescription;
    }


    @Override
    public Set<CaseSummary> getCandidateCases(
        final UserToken userToken,
        final S2sToken s2sToken
    ) {
        return elasticSearchHandler.searchCases(
            "DTSPB-6221-ROLLBACK",
            userToken,
            s2sToken,
            CaseType.GRANT_OF_REPRESENTATION,
            elasticQueries::getRollbackQuery
        );
    }

    @Override
    public MigrationEvent startEventForCase(
        final CaseSummary caseSummary,
        final UserToken userToken,
        final S2sToken s2sToken
    ) {

        final UserDetails userDetails =
            userToken.userDetails();

        final StartEventResponse startEventResponse =
            coreCaseDataApi.startEventForCaseWorker(
                userToken.getBearerToken(),
                s2sToken.s2sToken(),
                userDetails.getId(),
                "PROBATE",
                "GrantOfRepresentation",
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
            migrationEvent.startEventResponse()
                .getCaseDetails();

        final List<CaseEventDetail> caseEvents =
            caseEventsApi.findEventDetailsForCase(
                migrationEvent.userToken().getBearerToken(),
                migrationEvent.s2sToken().s2sToken(),
                migrationEvent.userToken().userDetails().getId(),
                caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId(),
                caseDetails.getId().toString()
            );

        final String originalLocation =
            findOriginalLocation(caseEvents);

        log.info(
            "Case {} originalLocation={}",
            migrationEvent.caseSummary().reference(),
            originalLocation
        );

        return originalLocation != null;

    }

    @Override
    public boolean migrate(
        final MigrationEvent migrationEvent
    ) {

        final StartEventResponse startEventResponse =
            migrationEvent.startEventResponse();

        final CaseDetails caseDetails =
            startEventResponse.getCaseDetails();

        final List<CaseEventDetail> caseEvents =
            caseEventsApi.findEventDetailsForCase(
                migrationEvent.userToken().getBearerToken(),
                migrationEvent.s2sToken().s2sToken(),
                migrationEvent.userToken().userDetails().getId(),
                caseDetails.getJurisdiction(),
                caseDetails.getCaseTypeId(),
                caseDetails.getId().toString()
            );

        final String originalLocation =
            findOriginalLocation(caseEvents);

        if (originalLocation == null) {

            log.warn(
                "DTSPB-6221 rollback: unable to determine original registryLocation for case {}",
                migrationEvent.caseSummary().reference()
            );

            return false;
        }

        final Map<String, Object> data =
            caseDetails.getData();

        if (data == null) {

            log.warn(
                "DTSPB-6221 rollback: no case data found for case {}",
                migrationEvent.caseSummary().reference()
            );

            return false;
        }

        final Object currentLocation =
            data.get("registryLocation");

        if (!"ctsc".equals(currentLocation)) {

            log.warn(
                "DTSPB-6221 rollback: case {} no longer has registryLocation ctsc",
                migrationEvent.caseSummary().reference()
            );

            return false;
        }

        data.put(
            "registryLocation",
            originalLocation
        );

        Event event = Event.builder()
            .id(startEventResponse.getEventId())
            .summary(ROLLBACK_SUMMARY)
            .description(ROLLBACK_DESCRIPTION)
            .build();

        CaseDataContent content =
            CaseDataContent.builder()
                .eventToken(startEventResponse.getToken())
                .event(event)
                .data(data)
                .build();

        if (reimplConfig.isDryRun()) {
            return true;
        }

        return coreCaseDataApi.submitEventForCaseWorker(
            migrationEvent.userToken().getBearerToken(),
            migrationEvent.s2sToken().s2sToken(),
            migrationEvent.userToken()
                .userDetails()
                .getId(),
            caseDetails.getJurisdiction(),
            caseDetails.getCaseTypeId(),
            caseDetails.getId().toString(),
            true,
            content
        ) != null;
    }

    private String findOriginalLocation(
        final List<CaseEventDetail> caseEvents
    ) {

        return caseEvents.stream()
            .filter(this::findDtspb6221MigrationEvent)
            .findFirst()
            .map(CaseEventDetail::getDescription)
            .filter(description -> description.contains("?"))
            .map(description -> description.split("\\?", 2)[1])
            .orElse(null);
    }
}
