package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113.Dtspb5113MigrationHandler.GRANT_ISSUED_DATE;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113.Dtspb5113MigrationHandler.GRANT_OF_REPRESENTATION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113.Dtspb5113MigrationHandler.JURISDICTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113.Dtspb5113MigrationHandler.MIGRATION_DESCRIPTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113.Dtspb5113MigrationHandler.MIGRATION_ID;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113.Dtspb5113MigrationHandler.MIGRATION_SUMMARY;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5113.Dtspb5113MigrationHandler.STATE_DORMANT;


class Dtspb5113MigrationHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;
    @Mock
    ElasticSearchHandler elasticSearchHandlerMock;
    @Mock
    Dtspb5113Config dtspb5113ConfigMock;
    @Mock
    Dtspb5113ElasticQueries dtspb5113ElasticQueriesMock;
    @Mock
    ReimplConfig reimplConfigMock;

    Dtspb5113MigrationHandler dtspb5113MigrationHandler;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        dtspb5113MigrationHandler = new Dtspb5113MigrationHandler(
                coreCaseDataApiMock,
                elasticSearchHandlerMock,
                reimplConfigMock,
                dtspb5113ConfigMock,
                dtspb5113ElasticQueriesMock);
    }

    @AfterEach
    void tearDown() {
        try {
            closeableMocks.close();
        } catch (Exception e) {
            // Nothing to do
        }
    }

    @Test
    void getCandidateCasesCallsElasticSearchOnce() {
        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();

        final CaseSummary gorCase = new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION);

        when(elasticSearchHandlerMock.searchCases(
                        any(),
                        any(),
                        any(),
                        eq(CaseType.GRANT_OF_REPRESENTATION),
                        any()))
                .thenReturn(Set.of(gorCase));

        final Set<CaseSummary> candidateCases = dtspb5113MigrationHandler.getCandidateCases(
                userToken,
                s2sToken);

        assertAll(
                () -> verify(elasticSearchHandlerMock).searchCases(
                        any(),
                        eq(userToken),
                        eq(s2sToken),
                        eq(CaseType.GRANT_OF_REPRESENTATION),
                        any()),
                () -> assertThat(candidateCases, hasSize(1)),
                () -> assertThat(candidateCases, contains(gorCase)));
    }

    @Test
    void startGorEvent() {
        final Long caseId = 1L;
        final CaseType caseType = CaseType.GRANT_OF_REPRESENTATION;
        final CaseSummary caseSummary = new CaseSummary(caseId, caseType);

        final String userId = UUID.randomUUID().toString();
        final UserDetails userDetails = mock();
        when(userDetails.getId())
                .thenReturn(userId);

        final String userBearerToken = UUID.randomUUID().toString();
        final UserToken userToken = mock();
        when(userToken.userDetails())
                .thenReturn(userDetails);
        when(userToken.getBearerToken())
                .thenReturn(userBearerToken);

        final String s2sBearerToken = UUID.randomUUID().toString();
        final S2sToken s2sToken = mock();
        when(s2sToken.s2sToken())
                .thenReturn(s2sBearerToken);

        final StartEventResponse startEventResponse = mock();
        when(coreCaseDataApiMock.startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(startEventResponse);

        final MigrationEvent actual = dtspb5113MigrationHandler.startEventForCase(
                caseSummary,
                userToken,
                s2sToken);

        assertAll(
                () -> assertSame(caseSummary, actual.caseSummary()),
                () -> assertSame(userToken, actual.userToken()),
                () -> assertSame(s2sToken, actual.s2sToken()),
                () -> assertSame(startEventResponse, actual.startEventResponse()),
                () -> verify(coreCaseDataApiMock).startEventForCaseWorker(
                        userBearerToken,
                        s2sBearerToken,
                        userId,
                        JURISDICTION,
                        GRANT_OF_REPRESENTATION,
                        caseId.toString(),
                        "boHistoryCorrection"));
    }


    @Test
    void shouldMigrateCaseNullDetailsThrows() {
        final MigrationEvent migrationEvent = mock();

        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        when(migrationEvent.caseSummary())
                .thenReturn(caseSummary);
        when(migrationEvent.startEventResponse())
                .thenReturn(startEventResponse);

        final CaseDetails caseDetails = null;
        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);

        assertThrows(
                Dtspb5113MigrationHandler.Dtspb5113MigrationException.class,
                () -> dtspb5113MigrationHandler.shouldMigrateCase(migrationEvent));
    }

    @Test
    void shouldMigrateCaseNullDataThrows() {
        final MigrationEvent migrationEvent = mock();

        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        when(migrationEvent.caseSummary())
                .thenReturn(caseSummary);
        when(migrationEvent.startEventResponse())
                .thenReturn(startEventResponse);

        final CaseDetails caseDetails = mock();
        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);

        final Map<String, Object> caseData = null;
        when(caseDetails.getData())
                .thenReturn(caseData);

        assertThrows(
                Dtspb5113MigrationHandler.Dtspb5113MigrationException.class,
                () -> dtspb5113MigrationHandler.shouldMigrateCase(migrationEvent));
    }

    @Test
    void shouldMigrateCaseWhenHasGrantIssuedDateAndDormant() {
        final MigrationEvent migrationEvent = mock();

        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        when(migrationEvent.caseSummary())
                .thenReturn(caseSummary);
        when(migrationEvent.startEventResponse())
                .thenReturn(startEventResponse);

        final CaseDetails caseDetails = mock();
        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);
        when(caseDetails.getState())
            .thenReturn(STATE_DORMANT);

        final Map<String, Object> caseData = Map.of(
            GRANT_ISSUED_DATE, "2025-01-02"
        );
        when(caseDetails.getData())
                .thenReturn(caseData);

        final boolean actual = dtspb5113MigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(true));
    }

    @Test
    void shouldNotMigrateCaseWhenNoGrantIssuedDate() {
        final MigrationEvent migrationEvent = mock();

        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        when(migrationEvent.caseSummary())
            .thenReturn(caseSummary);
        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        final CaseDetails caseDetails = mock();
        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        final Map<String, Object> caseData = Map.of();
        when(caseDetails.getData())
            .thenReturn(caseData);

        final boolean actual = dtspb5113MigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
    }

    @Test
    void migrateCaseAndCallsCcdSubmit() {
        final CaseSummary caseSummary = mock();

        final String eventId = UUID.randomUUID().toString();
        final String eventToken = UUID.randomUUID().toString();
        final StartEventResponse startEventResponse = mock();
        when(startEventResponse.getEventId())
                .thenReturn(eventId);
        when(startEventResponse.getToken())
                .thenReturn(eventToken);

        final UserToken userToken = mock();

        final UserDetails userDetails = mock();
        final String userBearer = UUID.randomUUID().toString();
        final String userId = UUID.randomUUID().toString();
        when(userToken.getBearerToken())
                .thenReturn(userBearer);
        when(userToken.userDetails())
                .thenReturn(userDetails);
        when(userDetails.getId())
                .thenReturn(userId);

        final String s2sBearer = UUID.randomUUID().toString();
        final S2sToken s2sToken = mock();
        when(s2sToken.s2sToken())
                .thenReturn(s2sBearer);

        final MigrationEvent migrationEvent = new MigrationEvent(
                caseSummary,
                startEventResponse,
                userToken,
                s2sToken);

        final Long caseId = 1L;
        final String jurisdiction = UUID.randomUUID().toString();
        final String caseType = UUID.randomUUID().toString();
        final CaseDetails caseDetails = mock();
        when(caseDetails.getId())
                .thenReturn(caseId);
        when(caseDetails.getJurisdiction())
                .thenReturn(jurisdiction);
        when(caseDetails.getCaseTypeId())
                .thenReturn(caseType);

        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);

        final Map<String, Object> caseData = new HashMap<>();
        when(caseDetails.getData())
                .thenReturn(caseData);

        final CaseDetails caseResult = mock();

        when(coreCaseDataApiMock.submitEventForCaseWorker(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                any()))
                        .thenReturn(caseResult);

        final boolean actual = dtspb5113MigrationHandler.migrate(migrationEvent);

        final ArgumentCaptor<CaseDataContent> dataCaptor = ArgumentCaptor.forClass(CaseDataContent.class);
        verify(coreCaseDataApiMock).submitEventForCaseWorker(
                eq(userBearer),
                eq(s2sBearer),
                eq(userId),
                eq(jurisdiction),
                eq(caseType),
                eq(caseId.toString()),
                eq(true),
                dataCaptor.capture());
        final CaseDataContent caseDataContent = dataCaptor.getValue();
        final Event event = caseDataContent.getEvent();

        assertAll(
                () -> assertThat(actual, equalTo(true)),
                () -> assertThat(caseDataContent.getEventToken(), equalTo(eventToken)),
                () -> assertThat(event.getId(), equalTo(eventId)),
                () -> assertThat(event.getSummary(), equalTo(MIGRATION_SUMMARY)),
                () -> assertThat(event.getDescription(), equalTo(MIGRATION_DESCRIPTION)));

        final Object migratedObj = caseDataContent.getData();
        if (!(migratedObj instanceof Map)) {
            fail("Migrated object is not a Map");
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> migratedData = (Map<String, Object>) migratedObj;

        assertThat(migratedData, aMapWithSize(1));
        assertThat(migratedData, hasKey("migrationCallbackMetadata"));

        final String metadata = (String) migratedData.get("migrationCallbackMetadata");
        final JSONObject metadataJson = new JSONObject(metadata);

        assertThat(metadataJson.getString("migrationId"), equalTo(MIGRATION_ID));
    }

    @Test
    void migrateDryRunReturnsTrueWithoutCallingCcd() {

        final CaseSummary caseSummary = mock();

        final StartEventResponse startEventResponse = mock();

        final UserToken userToken = mock();

        final UserDetails userDetails = mock();
        when(userToken.userDetails())
                .thenReturn(userDetails);

        final S2sToken s2sToken = mock();

        final MigrationEvent migrationEvent = new MigrationEvent(
                caseSummary,
                startEventResponse,
                userToken,
                s2sToken);

        final Long caseId = 1L;
        final CaseDetails caseDetails = mock();
        when(caseDetails.getId())
                .thenReturn(caseId);

        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);

        final Map<String, Object> caseData = new HashMap<>();
        when(caseDetails.getData())
                .thenReturn(caseData);

        when(reimplConfigMock.isDryRun())
                .thenReturn(true);

        final boolean actual = dtspb5113MigrationHandler.migrate(migrationEvent);

        assertAll(
                () -> assertThat(actual, equalTo(true)),
                () -> verify(coreCaseDataApiMock, never()).submitEventForCaseWorker(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any()));
    }

    @Test
    void migrateReturnsFalseIfCcdReturnsNull() {

        final CaseSummary caseSummary = mock();

        final StartEventResponse startEventResponse = mock();

        final UserToken userToken = mock();

        final UserDetails userDetails = mock();
        when(userToken.userDetails())
                .thenReturn(userDetails);

        final S2sToken s2sToken = mock();

        final MigrationEvent migrationEvent = new MigrationEvent(
                caseSummary,
                startEventResponse,
                userToken,
                s2sToken);

        final Long caseId = 1L;
        final CaseDetails caseDetails = mock();
        when(caseDetails.getId())
                .thenReturn(caseId);

        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);

        final Map<String, Object> caseData = new HashMap<>();
        when(caseDetails.getData())
                .thenReturn(caseData);

        final boolean actual = dtspb5113MigrationHandler.migrate(migrationEvent);

        assertAll(
                () -> assertThat(actual, equalTo(false)),
                () -> verify(coreCaseDataApiMock).submitEventForCaseWorker(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        anyBoolean(),
                        any()));
    }
}
