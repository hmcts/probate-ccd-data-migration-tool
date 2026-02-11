package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064.Dtspb5064MigrationHandler.CAVEAT;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064.Dtspb5064MigrationHandler.CAVEAT_MATCHING_STATE;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064.Dtspb5064MigrationHandler.JURISDICTION;

class Dtspb5064MigrationHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;
    @Mock
    ElasticSearchHandler elasticSearchHandlerMock;
    @Mock
    Dtspb5064Config dtspb5064ConfigMock;
    @Mock
    Dtspb5064ElasticQueries dtspb5064ElasticQueriesMock;

    Dtspb5064MigrationHandler dtspb5064MigrationHandler;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        dtspb5064MigrationHandler = new Dtspb5064MigrationHandler(
                coreCaseDataApiMock,
                elasticSearchHandlerMock,
                dtspb5064ConfigMock,
                dtspb5064ElasticQueriesMock);
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
    void getCandidateCasesCallsElasticSearchOnceForCaveat() {
        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();

        final CaseSummary caveatCase = new CaseSummary(2L, CaseType.CAVEAT);

        when(elasticSearchHandlerMock.searchCases(
                        any(),
                        any(),
                        any(),
                        eq(CaseType.CAVEAT),
                        any()))
                .thenReturn(Set.of(caveatCase));

        final Set<CaseSummary> candidateCases = dtspb5064MigrationHandler.getCandidateCases(
                userToken,
                s2sToken);

        assertAll(
                () -> verify(elasticSearchHandlerMock).searchCases(
                        anyString(),
                        eq(userToken),
                        eq(s2sToken),
                        eq(CaseType.CAVEAT),
                        any()),
                () -> assertThat(candidateCases, hasSize(1)),
                () -> assertThat(candidateCases, containsInAnyOrder(caveatCase)));
    }

    @Test
    void startCaveatEvent() {
        final Long caseId = 1L;
        final CaseType caseType = CaseType.CAVEAT;
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

        final MigrationEvent actual = dtspb5064MigrationHandler.startEventForCase(
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
                        CAVEAT,
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
                Dtspb5064MigrationHandler.Dtspb5064MigrationException.class,
                () -> dtspb5064MigrationHandler.shouldMigrateCase(migrationEvent));
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
                Dtspb5064MigrationHandler.Dtspb5064MigrationException.class,
                () -> dtspb5064MigrationHandler.shouldMigrateCase(migrationEvent));
    }

    @Test
    void shouldMigrateCaseWithCaveatMatchingStateTrue() {
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
            .thenReturn("CaveatNotMatched");
        final Map<String, Object> caseData = Map.of();
        when(caseDetails.getData())
                .thenReturn(caseData);

        final boolean actual = dtspb5064MigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(true));
    }

    @Test
    void shouldMigrateCaseWithCaveatMatchingStateFalse() {
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
            .thenReturn(CAVEAT_MATCHING_STATE);

        final Map<String, Object> caseData = Map.of(
                CAVEAT_MATCHING_STATE, "");
        when(caseDetails.getData())
                .thenReturn(caseData);

        final boolean actual = dtspb5064MigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
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

        when(dtspb5064ConfigMock.isDryRun())
                .thenReturn(true);

        final boolean actual = dtspb5064MigrationHandler.migrate(migrationEvent);

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

        final boolean actual = dtspb5064MigrationHandler.migrate(migrationEvent);

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
