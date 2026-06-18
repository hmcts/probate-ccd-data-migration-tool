package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.json.JSONObject;
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
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586.Dtspb5586RollbackMigrationHandler.GRANT_OF_REPRESENTATION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586.Dtspb5586RollbackMigrationHandler.JURISDICTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586.Dtspb5586RollbackMigrationHandler.ROLLBACK_DESCRIPTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586.Dtspb5586RollbackMigrationHandler.ROLLBACK_SUMMARY;

class Dtspb5586RollbackMigrationHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;
    @Mock
    CaseEventsApi caseEventsApiMock;
    @Mock
    ElasticSearchHandler elasticSearchHandlerMock;
    @Mock
    ReimplConfig reimplConfigMock;
    @Mock
    Dtspb5586Config dtspb5586ConfigMock;
    @Mock
    Dtspb5586ElasticQueries dtspb5586ElasticQueriesMock;

    Dtspb5586RollbackMigrationHandler dtspb5586RollbackMigrationHandler;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        dtspb5586RollbackMigrationHandler = new Dtspb5586RollbackMigrationHandler(
                coreCaseDataApiMock,
                caseEventsApiMock,
                elasticSearchHandlerMock,
                reimplConfigMock,
                dtspb5586ConfigMock,
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

    private void mockValidMigrationEvent() {
        CaseEventDetail caseEventDetail = mock();
        when(caseEventDetail.getId()).thenReturn("boHistoryCorrection");
        when(caseEventDetail.getDescription()).thenReturn("DTSPB-5586?[]");

        when(caseEventsApiMock.findEventDetailsForCase(
            any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of(caseEventDetail));
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

        final Set<CaseSummary> candidateCases = dtspb5586RollbackMigrationHandler.getCandidateCases(
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

        final Set<CaseSummary> candidateCases = dtspb5586RollbackMigrationHandler.getCandidateCases(
            userToken,
            s2sToken);

        assertAll(
            () -> verify(elasticSearchHandlerMock).searchCases(
                any(),
                eq(userToken),
                eq(s2sToken),
                eq(CaseType.GRANT_OF_REPRESENTATION),
                any()),
            () -> verify(dtspb5586ElasticQueriesMock).getGorRollbackQuery(any(), any(), any()),
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

        final MigrationEvent actual = dtspb5586RollbackMigrationHandler.startEventForCase(
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
                Dtspb5586RollbackMigrationHandler.Dtspb5586RollbackException.class,
                () -> dtspb5586RollbackMigrationHandler.shouldMigrateCase(migrationEvent));
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
                Dtspb5586RollbackMigrationHandler.Dtspb5586RollbackException.class,
                () -> dtspb5586RollbackMigrationHandler.shouldMigrateCase(migrationEvent));
    }


    @Test
    void shouldMigrateCaseReturnsFalse() {
        final MigrationEvent migrationEvent = mock();
        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();
        when(migrationEvent.caseSummary())
                .thenReturn(caseSummary);
        when(migrationEvent.startEventResponse())
                .thenReturn(startEventResponse);

        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();
        final UserDetails userDetails = mock();
        when(migrationEvent.userToken()).thenReturn(userToken);
        when(migrationEvent.s2sToken()).thenReturn(s2sToken);
        when(userToken.getBearerToken()).thenReturn(UUID.randomUUID().toString());
        when(userToken.userDetails()).thenReturn(userDetails);
        when(userDetails.getId()).thenReturn(UUID.randomUUID().toString());

        final CaseDetails caseDetails = mock();
        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);

        final Map<String, Object> caseData = Map.of();
        when(caseDetails.getData())
                .thenReturn(caseData);

        final boolean actual = dtspb5586RollbackMigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
    }

    @Test
    void migrateShouldShortCircuitOnDryRun() {
        when(reimplConfigMock.isDryRun())
                .thenReturn(true);

        final CaseSummary caseSummary = mock();

        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();
        final Map<String, Object> caseData = new HashMap<>();
        List<Map<String, Object>> boHandoffReasonList = new ArrayList<>();

        Map<String, Object> entry = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("caseHandoffReason", "AdmonWill");
        entry.put("value", value);
        boHandoffReasonList.add(entry);
        caseData.put("boHandoffReasonList", boHandoffReasonList);

        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);
        when(caseDetails.getData())
                .thenReturn(caseData);

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

        final S2sToken s2sToken = mock();

        mockValidMigrationEvent();

        final MigrationEvent migrationEvent = new MigrationEvent(caseSummary, startEventResponse, userToken, s2sToken);
        final boolean actual = dtspb5586RollbackMigrationHandler.migrate(migrationEvent);

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
    void migrateReturnFalseIfSubmitReturnsNull() {
        final CaseSummary caseSummary = mock();

        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();
        final Map<String, Object> caseData = new HashMap<>();

        List<Map<String, Object>> boHandoffReasonList = new ArrayList<>();

        Map<String, Object> entry = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("caseHandoffReason", "AdmonWill");
        entry.put("value", value);
        boHandoffReasonList.add(entry);
        caseData.put("boHandoffReasonList", boHandoffReasonList);

        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);
        when(caseDetails.getData())
                .thenReturn(caseData);

        final UserDetails userDetails = mock();
        final UserToken userToken = mock();
        when(userToken.userDetails())
                .thenReturn(userDetails);
        final S2sToken s2sToken = mock();

        mockValidMigrationEvent();

        final MigrationEvent migrationEvent = new MigrationEvent(caseSummary, startEventResponse, userToken, s2sToken);
        final boolean actual = dtspb5586RollbackMigrationHandler.migrate(migrationEvent);

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
    void migrateReturnTrueIfSubmitReturns() {
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

        List<Map<String, Object>> boHandoffReasonList = new ArrayList<>();

        Map<String, Object> entry = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("caseHandoffReason", "AdmonWill");
        entry.put("value", value);
        boHandoffReasonList.add(entry);
        caseData.put("boHandoffReasonList", boHandoffReasonList);
        caseData.put("caseHandedOffToLegacySite", "");

        final CaseDetails caseResult = mock();

        mockValidMigrationEvent();

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

        final boolean actual = dtspb5586RollbackMigrationHandler.migrate(migrationEvent);

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
                () -> assertThat(event.getSummary(), equalTo(ROLLBACK_SUMMARY)),
                () -> assertThat(event.getDescription(), equalTo(ROLLBACK_DESCRIPTION)));

        final Object migratedObj = caseDataContent.getData();
        if (!(migratedObj instanceof Map)) {
            fail("Migrated object is not a Map");
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> migratedData = (Map<String, Object>) migratedObj;

        assertThat(migratedData.get("caseHandedOffToLegacySite"), equalTo("Yes"));
        assertThat(((List<?>) migratedData.get("boHandoffReasonList")).size(), equalTo(1));
        final Object callbackMetadataObj = migratedData.get("caseHandedOffToLegacySite");
        final Object handoffReasonList = migratedData.get("boHandoffReasonList");
        if (!(callbackMetadataObj instanceof String)) {
            fail("Metadata object is not a String");
        }
        if (!(handoffReasonList instanceof ArrayList)) {
            fail("Metadata object is not a ArrayList");
        }
    }

    final Matcher<JSONObject> jsonHasString(final String key, final String value) {
        return new BaseMatcher<JSONObject>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("JSON object contains entry with key: " + key + " and value: " + value);
            }

            @Override
            public boolean matches(Object o) {
                if (!(o instanceof JSONObject)) {
                    return false;
                }
                final JSONObject jsonObject = (JSONObject) o;

                final boolean hasKey = jsonObject.has(key);
                if (!hasKey) {
                    return false;
                }

                final Object jsonValue = jsonObject.get(key);
                if (!(jsonValue instanceof String)) {
                    return false;
                }

                final String jsonValueString = (String) jsonValue;
                return jsonValueString.equals(value);
            }
        };
    }
}
