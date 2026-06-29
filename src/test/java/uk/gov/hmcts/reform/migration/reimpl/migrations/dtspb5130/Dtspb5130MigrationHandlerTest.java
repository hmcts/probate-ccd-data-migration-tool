package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130;

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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
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
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130MigrationHandler.GRANT_OF_REPRESENTATION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130MigrationHandler.JURISDICTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130MigrationHandler.MIGRATION_DESCRIPTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130MigrationHandler.MIGRATION_SUMMARY;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130MigrationHandler.NO;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130.Dtspb5130MigrationHandler.YES;

class Dtspb5130MigrationHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;
    @Mock
    ElasticSearchHandler elasticSearchHandlerMock;
    @Mock
    ReimplConfig reimplConfigMock;
    @Mock
    Dtspb5130ElasticQueries dtspb5130ElasticQueriesMock;
    Dtspb5130MigrationHandler dtspb5130MigrationHandler;
    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        dtspb5130MigrationHandler = new Dtspb5130MigrationHandler(
            coreCaseDataApiMock,
            elasticSearchHandlerMock,
            reimplConfigMock,
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
    void getCandidateCasesCallsElasticSearch() {
        UserToken userToken = mock();
        S2sToken s2sToken = mock();
        CaseSummary gorCase = new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION);
        when(elasticSearchHandlerMock.searchCases(
            any(),
            any(),
            any(),
            eq(CaseType.GRANT_OF_REPRESENTATION),
            any()))
            .thenReturn(Set.of(gorCase));
        Set<CaseSummary> candidateCases =
            dtspb5130MigrationHandler.getCandidateCases(userToken, s2sToken);
        verify(elasticSearchHandlerMock).searchCases(
            any(),
            eq(userToken),
            eq(s2sToken),
            eq(CaseType.GRANT_OF_REPRESENTATION),
            any());

        assertThat(candidateCases, containsInAnyOrder(gorCase));
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
        final MigrationEvent actual = dtspb5130MigrationHandler.startEventForCase(
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
            Dtspb5130MigrationHandler.Dtspb5130MigrationException.class,
            () -> dtspb5130MigrationHandler.shouldMigrateCase(migrationEvent));
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
            Dtspb5130MigrationHandler.Dtspb5130MigrationException.class,
            () -> dtspb5130MigrationHandler.shouldMigrateCase(migrationEvent));
    }

    @Test
    void shouldMigrateCaseEvidenceHandledNoTrue() {
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
        final Map<String, Object> caseData = Map.of("evidenceHandled", NO);
        when(caseDetails.getData())
            .thenReturn(caseData);
        final boolean actual = dtspb5130MigrationHandler.shouldMigrateCase(migrationEvent);
        assertThat(actual, equalTo(true));
    }

    @Test
    void shouldMigrateCaseEvidenceHandledYesFalse() {
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
        final Map<String, Object> caseData = Map.of("evidenceHandled", YES);
        when(caseDetails.getData())
            .thenReturn(caseData);
        final boolean actual = dtspb5130MigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
    }

    @Test
    void shouldMigrateCaseMissingEvidenceHandledFalse() {
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
        final boolean actual = dtspb5130MigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
    }

    @Test
    void shouldMigrateCaseNonStringEvidenceHandledFalse() {
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
        final Map<String, Object> caseData = Map.of("evidenceHandled", 123);
        when(caseDetails.getData())
            .thenReturn(caseData);
        final boolean actual = dtspb5130MigrationHandler.shouldMigrateCase(migrationEvent);
        assertThat(actual, equalTo(false));
    }

    @Test
    void migrateCaseSetsEvidenceHandledToYesAndCallsCcdSubmit() {
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
        caseData.put("evidenceHandled", NO);
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
        final boolean actual = dtspb5130MigrationHandler.migrate(migrationEvent);
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
            () -> assertThat(event.getSummary(), equalTo(MIGRATION_SUMMARY)),
            () -> assertThat(event.getDescription(), equalTo(MIGRATION_DESCRIPTION)),
            () -> assertThat(caseData.get("evidenceHandled"), equalTo(YES)));
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
        final boolean actual = dtspb5130MigrationHandler.migrate(migrationEvent);
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
        caseData.put("evidenceHandled", NO);
        when(caseDetails.getData())
            .thenReturn(caseData);
        when(reimplConfigMock.isDryRun())
            .thenReturn(true);
        final boolean actual = dtspb5130MigrationHandler.migrate(migrationEvent);
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
        caseData.put("evidenceHandled", NO);
        when(caseDetails.getData())
            .thenReturn(caseData);
        final boolean actual = dtspb5130MigrationHandler.migrate(migrationEvent);

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
