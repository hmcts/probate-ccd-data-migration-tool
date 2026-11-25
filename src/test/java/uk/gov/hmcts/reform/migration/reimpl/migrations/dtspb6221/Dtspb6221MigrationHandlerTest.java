package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb6221;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Dtspb6221MigrationHandlerTest {

    private CoreCaseDataApi coreCaseDataApi;
    private ElasticSearchHandler elasticSearchHandler;
    private ReimplConfig reimplConfig;
    private Dtspb6221ElasticQueries elasticQueries;

    private Dtspb6221MigrationHandler handler;

    @BeforeEach
    void setUp() {
        coreCaseDataApi = mock(CoreCaseDataApi.class);
        elasticSearchHandler = mock(ElasticSearchHandler.class);
        reimplConfig = mock(ReimplConfig.class);
        elasticQueries = mock(Dtspb6221ElasticQueries.class);

        handler = new Dtspb6221MigrationHandler(
            coreCaseDataApi,
            elasticSearchHandler,
            reimplConfig,
            elasticQueries
        );
    }

    @Test
    void shouldMigrateCaseForValidLocationAndState() {
        MigrationEvent migrationEvent = mock(MigrationEvent.class);
        CaseSummary caseSummary = mock(CaseSummary.class);

        when(migrationEvent.caseSummary()).thenReturn(caseSummary);
        when(caseSummary.reference()).thenReturn(123L);
        when(caseSummary.type()).thenReturn(CaseType.GRANT_OF_REPRESENTATION);

        Map<String, Object> data = new HashMap<>();
        data.put("registryLocation", "London");

        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getData())
            .thenReturn(data);

        when(caseDetails.getState())
            .thenReturn("BOReadyToIssue");

        assertThat(handler.shouldMigrateCase(migrationEvent))
            .isTrue();
    }

    @Test
    void shouldNotMigrateCaseWhenLocationNotSupported() {
        MigrationEvent migrationEvent = mock(MigrationEvent.class);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);

        Map<String, Object> data = new HashMap<>();
        data.put("registryLocation", "Leeds");

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getData())
            .thenReturn(data);

        when(caseDetails.getState())
            .thenReturn("BOReadyToIssue");

        assertThat(handler.shouldMigrateCase(migrationEvent))
            .isFalse();
    }

    @Test
    void shouldNotMigrateCaseWhenStateExcluded() {
        MigrationEvent migrationEvent = mock(MigrationEvent.class);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);

        Map<String, Object> data = new HashMap<>();
        data.put("registryLocation", "London");

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getData())
            .thenReturn(data);

        when(caseDetails.getState())
            .thenReturn("BOCaseMatchingReissue");

        assertThat(handler.shouldMigrateCase(migrationEvent))
            .isFalse();
    }

    @Test
    void shouldNotMigrateCaseWhenCaseDetailsNull() {
        MigrationEvent migrationEvent = mock(MigrationEvent.class);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);

        CaseSummary caseSummary = mock(CaseSummary.class);

        when(migrationEvent.caseSummary()).thenReturn(caseSummary);
        when(caseSummary.reference()).thenReturn(123L);
        when(caseSummary.type()).thenReturn(CaseType.GRANT_OF_REPRESENTATION);

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(null);

        assertThat(handler.shouldMigrateCase(migrationEvent))
            .isFalse();
    }

    @Test
    void shouldNotMigrateCaseWhenDataNull() {
        MigrationEvent migrationEvent = mock(MigrationEvent.class);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);

        CaseSummary caseSummary = mock(CaseSummary.class);

        when(migrationEvent.caseSummary()).thenReturn(caseSummary);
        when(caseSummary.reference()).thenReturn(123L);
        when(caseSummary.type()).thenReturn(CaseType.GRANT_OF_REPRESENTATION);

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getData())
            .thenReturn(null);

        assertThat(handler.shouldMigrateCase(migrationEvent))
            .isFalse();
    }

    @Test
    void shouldNotMigrateCaseWhenRegistryLocationMissing() {
        MigrationEvent migrationEvent = mock(MigrationEvent.class);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);

        CaseSummary caseSummary = mock(CaseSummary.class);

        when(migrationEvent.caseSummary()).thenReturn(caseSummary);
        when(caseSummary.reference()).thenReturn(123L);
        when(caseSummary.type()).thenReturn(CaseType.GRANT_OF_REPRESENTATION);

        Map<String, Object> data = new HashMap<>();

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getData())
            .thenReturn(data);

        when(caseDetails.getState())
            .thenReturn("BOReadyToIssue");

        assertThat(handler.shouldMigrateCase(migrationEvent))
            .isFalse();
    }

    @Test
    void shouldNotMigrateCaseWhenStateNull() {
        MigrationEvent migrationEvent = mock(MigrationEvent.class);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);

        Map<String, Object> data = new HashMap<>();
        data.put("registryLocation", "London");

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getData())
            .thenReturn(data);

        when(caseDetails.getState())
            .thenReturn(null);

        assertThat(handler.shouldMigrateCase(migrationEvent))
            .isFalse();
    }

    @Test
    void shouldReturnFalseWhenRegistryLocationIsNotString() {
        MigrationEvent migrationEvent = mock(MigrationEvent.class);
        CaseSummary caseSummary = mock(CaseSummary.class);

        Map<String, Object> data = new HashMap<>();
        data.put("registryLocation", 123);

        when(migrationEvent.caseSummary())
            .thenReturn(caseSummary);

        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(caseDetails.getData())
            .thenReturn(data);

        assertThat(handler.migrate(migrationEvent))
            .isFalse();
    }

    @Test
    void shouldUpdateRegistryLocationToCtscInDryRun() {
        MigrationEvent migrationEvent = mock(MigrationEvent.class);
        CaseSummary caseSummary = mock(CaseSummary.class);
        StartEventResponse startEventResponse = mock(StartEventResponse.class);
        CaseDetails caseDetails = mock(CaseDetails.class);

        Map<String, Object> data = new HashMap<>();
        data.put("registryLocation", "London");

        when(migrationEvent.caseSummary())
            .thenReturn(caseSummary);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(caseDetails.getData())
            .thenReturn(data);

        when(startEventResponse.getEventId())
            .thenReturn("boHistoryCorrection");

        when(startEventResponse.getToken())
            .thenReturn("token");

        when(caseSummary.reference())
            .thenReturn(123L);

        when(reimplConfig.isDryRun())
            .thenReturn(true);

        assertThat(handler.migrate(migrationEvent))
            .isTrue();

        assertThat(data).containsEntry("registryLocation", "ctsc");
    }
}
