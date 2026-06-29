package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130RollbackMigrationHandler.GRANT_OF_REPRESENTATION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130RollbackMigrationHandler.JURISDICTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130RollbackMigrationHandler.MIGRATION_EVENT;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130RollbackMigrationHandler.ROLLBACK_DESCRIPTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130RollbackMigrationHandler.ROLLBACK_SUMMARY;

class Dtspb5130RollbackMigrationHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;
    @Mock
    CaseEventsApi caseEventsApiMock;
    @Mock
    ElasticSearchHandler elasticSearchHandlerMock;
    @Mock
    ReimplConfig reimplConfigMock;
    @Mock
    Dtspb5130Config dtspb5130ConfigMock;
    @Mock
    Dtspb5130ElasticQueries dtspb5130ElasticQueriesMock;
    Dtspb5130RollbackMigrationHandler dtspb5130RollbackMigrationHandler;
    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        dtspb5130RollbackMigrationHandler = new Dtspb5130RollbackMigrationHandler(
            coreCaseDataApiMock,
            caseEventsApiMock,
            elasticSearchHandlerMock,
            reimplConfigMock,
            dtspb5130ConfigMock,
            dtspb5130ElasticQueriesMock);
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
    void getCandidateCasesCallsElasticQueryGet() {
        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();
        final CaseSummary gorCase = new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION);
        when(elasticSearchHandlerMock.searchCases(
            any(),
            any(),
            any(),
            eq(CaseType.GRANT_OF_REPRESENTATION),
            any()))
            .thenAnswer(invocation -> {
                final Function<Optional<Long>, org.json.JSONObject> f = invocation.getArgument(4);
                final org.json.JSONObject jsonObject = f.apply(Optional.of(1L));
                return Set.of(gorCase);
            });
        final Set<CaseSummary> candidateCases = dtspb5130RollbackMigrationHandler.getCandidateCases(
            userToken,
            s2sToken);
        assertAll(
            () -> verify(elasticSearchHandlerMock).searchCases(
                any(),
                eq(userToken),
                eq(s2sToken),
                eq(CaseType.GRANT_OF_REPRESENTATION),
                any()),
            () -> verify(dtspb5130ElasticQueriesMock).getGorRollbackQuery(any(), any(), any()),
            () -> assertThat(candidateCases, hasSize(1)),
            () -> assertThat(candidateCases, containsInAnyOrder(gorCase)));
    }

    @Test
    void startGorEvent() {
        final Long caseId = 1L;
        final CaseType caseType = CaseType.GRANT_OF_REPRESENTATION;
        final CaseSummary caseSummary = new CaseSummary(caseId, caseType);
        final String userId = "someUserId";
        final UserDetails userDetails = mock();
        when(userDetails.getId())
            .thenReturn(userId);
        final String userBearerToken = "someUserBearerToken";
        final UserToken userToken = mock();
        when(userToken.userDetails())
            .thenReturn(userDetails);
        when(userToken.getBearerToken())
            .thenReturn(userBearerToken);
        final String s2sBearerToken = "someS2sBearerToken";
        final S2sToken s2sToken = mock();
        when(s2sToken.s2sToken())
            .thenReturn(s2sBearerToken);
        final StartEventResponse startEventResponse = mock();
        when(coreCaseDataApiMock.startEventForCaseWorker(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(startEventResponse);
        final MigrationEvent actual = dtspb5130RollbackMigrationHandler.startEventForCase(
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
                "boCorrection"));
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
            Dtspb5130RollbackMigrationHandler.Dtspb5130RollbackException.class,
            () -> dtspb5130RollbackMigrationHandler.shouldMigrateCase(migrationEvent));
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
            Dtspb5130RollbackMigrationHandler.Dtspb5130RollbackException.class,
            () -> dtspb5130RollbackMigrationHandler.shouldMigrateCase(migrationEvent));
    }

    @Test
    void shouldMigrateCaseNoMatchingCaseEventsReturnsFalse() {
        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        final UserToken userToken = mock();
        final UserDetails userDetails = mock();
        final String userBearer = "someUserBearer";
        final String userId = "someUserId";
        when(userToken.getBearerToken())
            .thenReturn(userBearer);
        when(userToken.userDetails())
            .thenReturn(userDetails);
        when(userDetails.getId())
            .thenReturn(userId);
        final String s2sBearer = "someS2sBearer";
        final S2sToken s2sToken = mock();
        when(s2sToken.s2sToken())
            .thenReturn(s2sBearer);
        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken);
        final Long caseId = 1L;
        final String caseType = "someCaseType";
        final CaseDetails caseDetails = mock();
        when(caseDetails.getId())
            .thenReturn(caseId);
        when(caseDetails.getJurisdiction())
            .thenReturn(JURISDICTION);
        when(caseDetails.getCaseTypeId())
            .thenReturn(caseType);
        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);
        final Map<String, Object> caseData = Map.of();
        when(caseDetails.getData())
            .thenReturn(caseData);
        when(caseEventsApiMock.findEventDetailsForCase(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of());
        final boolean actual = dtspb5130RollbackMigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
        verify(caseEventsApiMock).findEventDetailsForCase(
            userBearer,
            s2sBearer,
            userId,
            JURISDICTION,
            caseType,
            caseId.toString());
    }

    @Test
    void shouldMigrateCaseMatchingCaseEventReturnsTrue() {
        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        final UserToken userToken = mock();
        final UserDetails userDetails = mock();
        final String userBearer = "someUserBearer";
        final String userId = "someUserId";
        when(userToken.getBearerToken())
            .thenReturn(userBearer);
        when(userToken.userDetails())
            .thenReturn(userDetails);
        when(userDetails.getId())
            .thenReturn(userId);
        final String s2sBearer = "someS2sBearer";
        final S2sToken s2sToken = mock();
        when(s2sToken.s2sToken())
            .thenReturn(s2sBearer);
        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken);
        final Long caseId = 1L;
        final String caseType = "someCaseType";
        final CaseDetails caseDetails = mock();
        when(caseDetails.getId())
            .thenReturn(caseId);
        when(caseDetails.getJurisdiction())
            .thenReturn(JURISDICTION);
        when(caseDetails.getCaseTypeId())
            .thenReturn(caseType);
        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);
        final Map<String, Object> caseData = Map.of();
        when(caseDetails.getData())
            .thenReturn(caseData);
        final CaseEventDetail caseEventDetail = mock();
        when(caseEventDetail.getId())
            .thenReturn(MIGRATION_EVENT);
        when(caseEventDetail.getDescription())
            .thenReturn(Dtspb5130MigrationHandler.MIGRATION_DESCRIPTION);
        when(caseEventsApiMock.findEventDetailsForCase(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(caseEventDetail));
        final boolean actual = dtspb5130RollbackMigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(true));
        verify(caseEventsApiMock).findEventDetailsForCase(
            userBearer,
            s2sBearer,
            userId,
            JURISDICTION,
            caseType,
            caseId.toString());
    }

    @Test
    void shouldMigrateCaseNonMatchingCaseEventReturnsFalse() {
        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        final UserToken userToken = mock();
        final UserDetails userDetails = mock();
        when(userToken.getBearerToken())
            .thenReturn("someUserBearer");
        when(userToken.userDetails())
            .thenReturn(userDetails);
        when(userDetails.getId())
            .thenReturn("someUserId");
        final S2sToken s2sToken = mock();
        when(s2sToken.s2sToken())
            .thenReturn("someS2sBearer");
        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken);
        final CaseDetails caseDetails = mock();
        when(caseDetails.getId())
            .thenReturn(1L);
        when(caseDetails.getJurisdiction())
            .thenReturn(JURISDICTION);
        when(caseDetails.getCaseTypeId())
            .thenReturn("someCaseType");
        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);
        final Map<String, Object> caseData = Map.of();
        when(caseDetails.getData())
            .thenReturn(caseData);
        final CaseEventDetail caseEventDetail = mock();
        when(caseEventDetail.getId())
            .thenReturn("someOtherEvent");
        when(caseEventDetail.getDescription())
            .thenReturn("some other description");
        when(caseEventsApiMock.findEventDetailsForCase(any(), any(), any(), any(), any(), any()))
            .thenReturn(List.of(caseEventDetail));
        final boolean actual = dtspb5130RollbackMigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
    }

    @Test
    void migrateSetsEvidenceHandledToNoWhenYes() {
        final CaseSummary caseSummary = mock();
        final String eventId = "someEventId";
        final String eventToken = "someEventToken";
        final StartEventResponse startEventResponse = mock();
        when(startEventResponse.getEventId())
            .thenReturn(eventId);
        when(startEventResponse.getToken())
            .thenReturn(eventToken);
        final UserToken userToken = mock();
        final UserDetails userDetails = mock();
        final String userBearer = "someUserBearer";
        final String userId = "someUserId";
        when(userToken.getBearerToken())
            .thenReturn(userBearer);
        when(userToken.userDetails())
            .thenReturn(userDetails);
        when(userDetails.getId())
            .thenReturn(userId);
        final String s2sBearer = "someS2sBearer";
        final S2sToken s2sToken = mock();
        when(s2sToken.s2sToken())
            .thenReturn(s2sBearer);
        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken);
        final Long caseId = 1L;
        final String caseType = "someCaseType";
        final CaseDetails caseDetails = mock();
        when(caseDetails.getId())
            .thenReturn(caseId);
        when(caseDetails.getJurisdiction())
            .thenReturn(JURISDICTION);
        when(caseDetails.getCaseTypeId())
            .thenReturn(caseType);
        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);
        final Map<String, Object> caseData = new HashMap<>();
        caseData.put("evidenceHandled", "Yes");
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
        final boolean actual = dtspb5130RollbackMigrationHandler.migrate(migrationEvent);
        final ArgumentCaptor<CaseDataContent> dataCaptor = ArgumentCaptor.forClass(CaseDataContent.class);
        verify(coreCaseDataApiMock).submitEventForCaseWorker(
            eq(userBearer),
            eq(s2sBearer),
            eq(userId),
            eq(JURISDICTION),
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
            () -> assertThat(event.getSummary(), equalTo(ROLLBACK_SUMMARY)),
            () -> assertThat(event.getDescription(), equalTo(ROLLBACK_DESCRIPTION)),
            () -> assertThat(caseData.get("evidenceHandled"), equalTo("No")));
    }

    @Test
    void migrateReturnsFalseWhenEvidenceHandledNotString() {
        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();
        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken);
        final CaseDetails caseDetails = mock();
        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);
        final Map<String, Object> caseData = new HashMap<>();
        caseData.put("evidenceHandled", 123);
        when(caseDetails.getData())
            .thenReturn(caseData);
        final boolean actual = dtspb5130RollbackMigrationHandler.migrate(migrationEvent);

        assertAll(
            () -> assertThat(actual, equalTo(false)),
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
    void migrateDryRunReturnsTrueWithoutCallingCcd() {
        when(reimplConfigMock.isDryRun())
            .thenReturn(true);
        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();
        final Map<String, Object> caseData = new HashMap<>();
        caseData.put("evidenceHandled", "Yes");
        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);
        when(caseDetails.getData())
            .thenReturn(caseData);
        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();
        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary, startEventResponse, userToken, s2sToken);
        final boolean actual = dtspb5130RollbackMigrationHandler.migrate(migrationEvent);

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
        final CaseDetails caseDetails = mock();
        final Map<String, Object> caseData = new HashMap<>();
        caseData.put("evidenceHandled", "Yes");
        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);
        when(caseDetails.getData())
            .thenReturn(caseData);
        final UserDetails userDetails = mock();
        final UserToken userToken = mock();
        when(userToken.userDetails())
            .thenReturn(userDetails);
        final S2sToken s2sToken = mock();

        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary, startEventResponse, userToken, s2sToken);

        final boolean actual = dtspb5130RollbackMigrationHandler.migrate(migrationEvent);

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

    @Test
    void shouldMigrateCaseCaseEventsApiReturnsNullThrowsException() {
        final MigrationEvent migrationEvent = mock();
        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();

        when(migrationEvent.caseSummary()).thenReturn(caseSummary);
        when(migrationEvent.startEventResponse()).thenReturn(startEventResponse);
        when(startEventResponse.getCaseDetails()).thenReturn(caseDetails);

        when(caseDetails.getData()).thenReturn(Map.of());
        when(caseDetails.getId()).thenReturn(1L);
        when(caseDetails.getJurisdiction()).thenReturn(JURISDICTION);
        when(caseDetails.getCaseTypeId()).thenReturn("someCaseType");

        when(migrationEvent.userToken()).thenReturn(mock(UserToken.class));
        when(migrationEvent.s2sToken()).thenReturn(mock(S2sToken.class));

        when(caseEventsApiMock.findEventDetailsForCase(
            any(), any(), any(), any(), any(), any()
        )).thenReturn(null);

        assertThrows(NullPointerException.class,
            () -> dtspb5130RollbackMigrationHandler.shouldMigrateCase(migrationEvent));
    }
}
