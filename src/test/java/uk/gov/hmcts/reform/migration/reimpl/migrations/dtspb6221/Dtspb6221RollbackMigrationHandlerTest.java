package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb6221;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.CaseEventsApi;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.CaseEventDetail;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Dtspb6221RollbackMigrationHandlerTest {

    private CoreCaseDataApi coreCaseDataApi;
    private ElasticSearchHandler elasticSearchHandler;
    private Dtspb6221ElasticQueries elasticQueries;
    private ReimplConfig reimplConfig;
    private CaseEventsApi caseEventsApi;

    private Dtspb6221RollbackMigrationHandler handler;

    @BeforeEach
    void setUp() {
        coreCaseDataApi = mock(CoreCaseDataApi.class);
        elasticSearchHandler = mock(ElasticSearchHandler.class);
        elasticQueries = mock(Dtspb6221ElasticQueries.class);
        reimplConfig = mock(ReimplConfig.class);
        caseEventsApi = mock(CaseEventsApi.class);

        handler = new Dtspb6221RollbackMigrationHandler(
            coreCaseDataApi,
            elasticSearchHandler,
            elasticQueries,
            reimplConfig,
            caseEventsApi
        );
    }

    @Test
    void shouldFindDtspb6221MigrationEvent() {

        CaseEventDetail caseEventDetail =
            CaseEventDetail.builder()
                .id("boHistoryCorrection")
                .description(
                    Dtspb6221MigrationHandler.MIGRATION_DESCRIPTION
                )
                .build();

        assertThat(
            handler.findDtspb6221MigrationEvent(
                caseEventDetail
            )
        ).isTrue();
    }

    @Test
    void shouldNotFindDtspb6221MigrationEvent() {

        CaseEventDetail caseEventDetail =
            CaseEventDetail.builder()
                .id("boHistoryCorrection")
                .description("Some other migration")
                .build();

        assertThat(
            handler.findDtspb6221MigrationEvent(
                caseEventDetail
            )
        ).isFalse();
    }

    @Test
    void shouldMigrateCaseWhenMigrationEventExists() {

        StartEventResponse startEventResponse =
            mock(StartEventResponse.class);

        MigrationEvent migrationEvent =
            buildMigrationEvent(startEventResponse);

        CaseDetails caseDetails =
            mock(CaseDetails.class);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getJurisdiction())
            .thenReturn("PROBATE");

        when(caseDetails.getCaseTypeId())
            .thenReturn("GrantOfRepresentation");

        when(caseDetails.getId())
            .thenReturn(123L);

        CaseEventDetail previousEvent =
            CaseEventDetail.builder()
                .data(
                    Map.of(
                        "registryLocation",
                        "London"
                    )
                )
                .build();

        CaseEventDetail migrationHistoryEvent =
            CaseEventDetail.builder()
                .id("boHistoryCorrection")
                .description(
                    Dtspb6221MigrationHandler.MIGRATION_DESCRIPTION
                )
                .build();

        when(caseEventsApi.findEventDetailsForCase(
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )).thenReturn(
            List.of(
                previousEvent,
                migrationHistoryEvent
            )
        );

        assertThat(
            handler.shouldMigrateCase(
                migrationEvent
            )
        ).isTrue();
    }

    @Test
    void shouldNotMigrateCaseWhenMigrationEventDoesNotExist() {



        StartEventResponse startEventResponse =
            mock(StartEventResponse.class);

        MigrationEvent migrationEvent =
            buildMigrationEvent(startEventResponse);

        CaseDetails caseDetails =
            mock(CaseDetails.class);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getJurisdiction())
            .thenReturn("PROBATE");

        when(caseDetails.getCaseTypeId())
            .thenReturn("GrantOfRepresentation");

        when(caseDetails.getId())
            .thenReturn(123L);

        when(caseEventsApi.findEventDetailsForCase(
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )).thenReturn(
            List.of(
                CaseEventDetail.builder()
                    .id("someEvent")
                    .description("some description")
                    .build()
            )
        );

        assertThat(
            handler.shouldMigrateCase(
                migrationEvent
            )
        ).isFalse();
    }

    @Test
    void shouldRestoreOriginalLocationInDryRunMode() {

        StartEventResponse startEventResponse =
            mock(StartEventResponse.class);

        CaseDetails caseDetails =
            mock(CaseDetails.class);

        Map<String, Object> data =
            new HashMap<>();

        data.put(
            "registryLocation",
            "ctsc"
        );

        MigrationEvent migrationEvent =
            buildMigrationEvent(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getData())
            .thenReturn(data);

        when(caseDetails.getJurisdiction())
            .thenReturn("PROBATE");

        when(caseDetails.getCaseTypeId())
            .thenReturn("GrantOfRepresentation");

        when(caseDetails.getId())
            .thenReturn(123L);

        when(startEventResponse.getEventId())
            .thenReturn("boHistoryCorrection");

        when(startEventResponse.getToken())
            .thenReturn("token");

        CaseEventDetail previousEvent =
            CaseEventDetail.builder()
                .data(
                    Map.of(
                        "registryLocation",
                        "London"
                    )
                )
                .build();

        CaseEventDetail migrationHistoryEvent =
            CaseEventDetail.builder()
                .id("boHistoryCorrection")
                .description(
                    Dtspb6221MigrationHandler.MIGRATION_DESCRIPTION
                )
                .build();

        when(caseEventsApi.findEventDetailsForCase(
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )).thenReturn(
            List.of(
                previousEvent,
                migrationHistoryEvent
            )
        );

        when(reimplConfig.isDryRun())
            .thenReturn(true);

        boolean result =
            handler.migrate(migrationEvent);

        assertThat(result).isTrue();

        assertThat(data)
            .containsEntry(
                "registryLocation",
                "London"
            );
    }

    @Test
    void shouldNotRollbackIfCurrentLocationIsNotCtsc() {

        StartEventResponse startEventResponse =
            mock(StartEventResponse.class);

        CaseDetails caseDetails =
            mock(CaseDetails.class);

        Map<String, Object> data =
            new HashMap<>();

        data.put(
            "registryLocation",
            "Manchester"
        );

        MigrationEvent migrationEvent =
            buildMigrationEvent(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getData())
            .thenReturn(data);

        when(caseDetails.getJurisdiction())
            .thenReturn("PROBATE");

        when(caseDetails.getCaseTypeId())
            .thenReturn("GrantOfRepresentation");

        when(caseDetails.getId())
            .thenReturn(123L);

        CaseEventDetail previousEvent =
            CaseEventDetail.builder()
                .data(
                    Map.of(
                        "registryLocation",
                        "London"
                    )
                )
                .build();

        CaseEventDetail migrationHistoryEvent =
            CaseEventDetail.builder()
                .id("boHistoryCorrection")
                .description(
                    Dtspb6221MigrationHandler.MIGRATION_DESCRIPTION
                )
                .build();

        when(caseEventsApi.findEventDetailsForCase(
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )).thenReturn(
            List.of(
                previousEvent,
                migrationHistoryEvent
            )
        );

        assertThat(
            handler.migrate(
                migrationEvent
            )
        ).isFalse();
    }

    private MigrationEvent buildMigrationEvent(
        final StartEventResponse startEventResponse
    ) {

        UserDetails userDetails =
            mock(UserDetails.class);

        when(userDetails.getId())
            .thenReturn("123");

        UserToken userToken =
            mock(UserToken.class);

        when(userToken.getBearerToken())
            .thenReturn("bearer");

        when(userToken.userDetails())
            .thenReturn(userDetails);

        S2sToken s2sToken =
            mock(S2sToken.class);

        when(s2sToken.s2sToken())
            .thenReturn("s2s");

        CaseSummary caseSummary =
            new CaseSummary(
                123L,
                CaseType.GRANT_OF_REPRESENTATION
            );

        return new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken
        );
    }
}
