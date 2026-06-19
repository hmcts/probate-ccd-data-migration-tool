package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586.Dtspb5586MigrationHandler.GRANT_OF_REPRESENTATION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586.Dtspb5586MigrationHandler.JURISDICTION;

class Dtspb5586MigrationHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;
    @Mock
    ElasticSearchHandler elasticSearchHandlerMock;
    @Mock
    ReimplConfig reimplConfigMock;
    @Mock
    Dtspb5586ElasticQueries dtspb5586ElasticQueriesMock;

    Dtspb5586MigrationHandler dtspb5586MigrationHandler;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        dtspb5586MigrationHandler = new Dtspb5586MigrationHandler(
                coreCaseDataApiMock,
                elasticSearchHandlerMock,
                reimplConfigMock,
                dtspb5586ElasticQueriesMock);
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

        final Set<CaseSummary> candidateCases = dtspb5586MigrationHandler.getCandidateCases(
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
                () -> assertThat(candidateCases, containsInAnyOrder(gorCase)));
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
                final Function<Optional<Long>, JSONObject> f = invocation.getArgument(4);
                final JSONObject jsonObject = f.apply(Optional.of(1L));
                return Set.of(gorCase);
            });

        final Set<CaseSummary> candidateCases = dtspb5586MigrationHandler.getCandidateCases(
            userToken,
            s2sToken);

        assertAll(
            () -> verify(elasticSearchHandlerMock).searchCases(
                any(),
                eq(userToken),
                eq(s2sToken),
                eq(CaseType.GRANT_OF_REPRESENTATION),
                any()),
            () -> verify(dtspb5586ElasticQueriesMock).getGorMigrationQuery(any(), any()),
            () -> assertThat(candidateCases, hasSize(1)),
            () -> assertThat(candidateCases, containsInAnyOrder(gorCase)));
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

        final MigrationEvent actual = dtspb5586MigrationHandler.startEventForCase(
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
                Dtspb5586MigrationHandler.Dtspb5586MigrationException.class,
                () -> dtspb5586MigrationHandler.shouldMigrateCase(migrationEvent));
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
                Dtspb5586MigrationHandler.Dtspb5586MigrationException.class,
                () -> dtspb5586MigrationHandler.shouldMigrateCase(migrationEvent));
    }

    @Test
    void shouldMigrateCaseHandoffReasonsFilled() {
        final MigrationEvent migrationEvent = mock();

        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        when(migrationEvent.caseSummary()).thenReturn(caseSummary);
        when(migrationEvent.startEventResponse()).thenReturn(startEventResponse);

        final CaseDetails caseDetails = mock();
        when(startEventResponse.getCaseDetails()).thenReturn(caseDetails);

        Map<String, Object> innerValue = Map.of(
            "caseHandoffReason", "AdmonWill"
        );
        Map<String, Object> valueWrapper = Map.of(
            "value", innerValue
        );
        List<Object> boHandoffReasonList = List.of(valueWrapper);
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("boHandoffReasonList", boHandoffReasonList);
        when(caseDetails.getData()).thenReturn(caseData);

        final boolean actual = dtspb5586MigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(true));
    }

    @Test
    void migrateDryRunReturnsTrueWithoutCallingCcd() {

        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        final UserToken userToken = mock();
        final UserDetails userDetails = mock();
        when(userToken.userDetails()).thenReturn(userDetails);

        final S2sToken s2sToken = mock();

        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken);

        final Long caseId = 1L;
        final CaseDetails caseDetails = mock();
        when(caseDetails.getId()).thenReturn(caseId);

        when(startEventResponse.getCaseDetails()).thenReturn(caseDetails);

        Map<String, Object> innerValue = Map.of("caseHandoffReason", "AdmonWill");
        Map<String, Object> valueWrapper = Map.of(
            "value", innerValue
        );
        List<Object> boHandoffReasonList = new ArrayList<>(List.of(valueWrapper));
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("boHandoffReasonList", boHandoffReasonList);

        when(caseDetails.getData()).thenReturn(caseData);

        // Dry run enabled
        when(reimplConfigMock.isDryRun()).thenReturn(true);

        final boolean actual = dtspb5586MigrationHandler.migrate(migrationEvent);

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
                any())
        );
    }

    @Test
    void migrateReturnsFalseIfCcdReturnsNull() {

        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        final UserToken userToken = mock();
        final UserDetails userDetails = mock();
        when(userToken.userDetails()).thenReturn(userDetails);
        final S2sToken s2sToken = mock();

        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken);

        final CaseDetails caseDetails = mock();
        when(startEventResponse.getCaseDetails()).thenReturn(caseDetails);

        when(caseDetails.getData()).thenReturn(new HashMap<>());

        final boolean actual = dtspb5586MigrationHandler.migrate(migrationEvent);

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
                any())
        );
    }

    @Test
    void migrateReturnsTrueWhenCcdSubmissionSucceeds() {

        final Long caseId = 123L;
        final CaseSummary caseSummary = new CaseSummary(caseId, CaseType.GRANT_OF_REPRESENTATION);
        final UserDetails userDetails = mock();
        when(userDetails.getId()).thenReturn("user-1");
        final UserToken userToken = mock();
        when(userToken.userDetails()).thenReturn(userDetails);
        when(userToken.getBearerToken()).thenReturn("some-token");
        final S2sToken s2sToken = mock();
        when(s2sToken.s2sToken()).thenReturn("s2s-token");
        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();

        when(startEventResponse.getCaseDetails()).thenReturn(caseDetails);
        when(caseDetails.getId()).thenReturn(caseId);
        when(caseDetails.getJurisdiction()).thenReturn("PROBATE");
        when(caseDetails.getCaseTypeId()).thenReturn("GrantOfRepresentation");
        Map<String, Object> innerValue = Map.of("caseHandoffReason", "AdmonWill");
        Map<String, Object> valueWrapper = Map.of("value", innerValue);
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("boHandoffReasonList", List.of(valueWrapper));
        when(caseDetails.getData()).thenReturn(caseData);
        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken
        );

        when(reimplConfigMock.isDryRun()).thenReturn(false);
        when(coreCaseDataApiMock.submitEventForCaseWorker(
            any(), any(), any(), any(), any(), any(), anyBoolean(), any()
        )).thenReturn(mock(CaseDetails.class));
        final boolean result = dtspb5586MigrationHandler.migrate(migrationEvent);

        assertThat(result, equalTo(true));
        verify(coreCaseDataApiMock).submitEventForCaseWorker(
            eq("some-token"),
            eq("s2s-token"),
            eq("user-1"),
            eq("PROBATE"),
            eq("GrantOfRepresentation"),
            eq(caseId.toString()),
            eq(true),
            any()
        );
    }

    @Test
    void migrateReturnsFalseWhenCcdSubmissionReturnsNull() {

        final Long caseId = 456L;
        final CaseSummary caseSummary = new CaseSummary(caseId, CaseType.GRANT_OF_REPRESENTATION);
        final UserDetails userDetails = mock();
        when(userDetails.getId()).thenReturn("user-2");
        final UserToken userToken = mock();
        when(userToken.userDetails()).thenReturn(userDetails);
        when(userToken.getBearerToken()).thenReturn("some-token-2");
        final S2sToken s2sToken = mock();
        when(s2sToken.s2sToken()).thenReturn("s2s-token-2");
        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();

        when(startEventResponse.getCaseDetails()).thenReturn(caseDetails);
        when(caseDetails.getId()).thenReturn(caseId);
        when(caseDetails.getJurisdiction()).thenReturn("PROBATE");
        when(caseDetails.getCaseTypeId()).thenReturn("GrantOfRepresentation");
        Map<String, Object> innerValue = Map.of("caseHandoffReason", "ExtendedIntestacy");
        Map<String, Object> valueWrapper = Map.of("value", innerValue);
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("boHandoffReasonList", List.of(valueWrapper));
        when(caseDetails.getData()).thenReturn(caseData);
        final MigrationEvent migrationEvent = new MigrationEvent(
            caseSummary,
            startEventResponse,
            userToken,
            s2sToken
        );

        when(reimplConfigMock.isDryRun()).thenReturn(false);
        when(coreCaseDataApiMock.submitEventForCaseWorker(
            any(), any(), any(), any(), any(), any(), anyBoolean(), any()
        )).thenReturn(null);
        final boolean result = dtspb5586MigrationHandler.migrate(migrationEvent);
        assertThat(result, equalTo(false));
        verify(coreCaseDataApiMock).submitEventForCaseWorker(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any()
        );
    }

    @Test
    void shouldMigrateCaseReturnsFalseWhenHandoffListIsNotAList() {

        final MigrationEvent migrationEvent = mock();
        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();
        final CaseSummary caseSummary = mock();

        when(migrationEvent.caseSummary()).thenReturn(caseSummary);
        when(migrationEvent.startEventResponse()).thenReturn(startEventResponse);
        when(startEventResponse.getCaseDetails()).thenReturn(caseDetails);
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("boHandoffReasonList", "NOT_A_LIST");
        when(caseDetails.getData()).thenReturn(caseData);

        final boolean result = dtspb5586MigrationHandler.shouldMigrateCase(migrationEvent);
        assertThat(result, equalTo(false));
    }

    @Test
    void shouldMigrateCaseSkipsWhenListEntryIsNotMap() {

        final MigrationEvent migrationEvent = mock();
        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();
        final CaseSummary caseSummary = mock();

        when(migrationEvent.caseSummary()).thenReturn(caseSummary);
        when(migrationEvent.startEventResponse()).thenReturn(startEventResponse);
        when(startEventResponse.getCaseDetails()).thenReturn(caseDetails);
        List<Object> badList = List.of("I_AM_NOT_A_MAP");
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("boHandoffReasonList", badList);
        when(caseDetails.getData()).thenReturn(caseData);

        final boolean result = dtspb5586MigrationHandler.shouldMigrateCase(migrationEvent);
        assertThat(result, equalTo(false));
    }

    @Test
    void shouldMigrateCaseSkipsWhenValueIsNotMap() {

        final MigrationEvent migrationEvent = mock();
        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();
        final CaseSummary caseSummary = mock();

        when(migrationEvent.caseSummary()).thenReturn(caseSummary);
        when(migrationEvent.startEventResponse()).thenReturn(startEventResponse);
        when(startEventResponse.getCaseDetails()).thenReturn(caseDetails);
        Map<String, Object> badValueWrapper = Map.of("value", "NOT_A_MAP");
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("boHandoffReasonList", List.of(badValueWrapper));
        when(caseDetails.getData()).thenReturn(caseData);

        final boolean result = dtspb5586MigrationHandler.shouldMigrateCase(migrationEvent);
        assertThat(result, equalTo(false));
    }

}
