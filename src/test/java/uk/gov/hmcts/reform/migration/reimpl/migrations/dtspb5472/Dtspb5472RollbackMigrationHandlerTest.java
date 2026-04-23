package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472;

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
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
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
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.PA_ADOPTED_CHILD;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.PA_CHILD;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.PA_RELATIONSHIP_TO_DECEASED;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.SOL_ADOPTED_CHILD;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.SOL_CHILD;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.SOL_RELATIONSHIP_TO_DECEASED;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler.YES;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472RollbackMigrationHandler.GRANT_OF_REPRESENTATION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472RollbackMigrationHandler.JURISDICTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472RollbackMigrationHandler.MIGRATION_EVENT;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472RollbackMigrationHandler.PA_ADOPTED_IN;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472RollbackMigrationHandler.ROLLBACK_DESCRIPTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472RollbackMigrationHandler.ROLLBACK_ID;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472RollbackMigrationHandler.ROLLBACK_SUMMARY;

class Dtspb5472RollbackMigrationHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;
    @Mock
    CaseEventsApi caseEventsApiMock;
    @Mock
    ElasticSearchHandler elasticSearchHandlerMock;
    @Mock
    Dtspb5472Config dtspb5472ConfigMock;
    @Mock
    Dtspb5472ElasticQueries dtspb5472ElasticQueriesMock;

    Dtspb5472RollbackMigrationHandler dtspb5472RollbackMigrationHandler;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        dtspb5472RollbackMigrationHandler = new Dtspb5472RollbackMigrationHandler(
                coreCaseDataApiMock,
                caseEventsApiMock,
                elasticSearchHandlerMock,
                dtspb5472ConfigMock,
                dtspb5472ElasticQueriesMock);
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

        final Set<CaseSummary> candidateCases = dtspb5472RollbackMigrationHandler.getCandidateCases(
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

        final MigrationEvent actual = dtspb5472RollbackMigrationHandler.startEventForCase(
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
                Dtspb5472RollbackMigrationHandler.Dtspb5472RollbackException.class,
                () -> dtspb5472RollbackMigrationHandler.shouldMigrateCase(migrationEvent));
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
                Dtspb5472RollbackMigrationHandler.Dtspb5472RollbackException.class,
                () -> dtspb5472RollbackMigrationHandler.shouldMigrateCase(migrationEvent));
    }

    @Test
    void shouldMigrateCaseNoPaAdoptedInDataReturnsFalse() {
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

        final boolean actual = dtspb5472RollbackMigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
    }

    @Test
    void shouldMigrateCasePaAdoptedInDataNoCaseEventsReturnsFalse() {
        final CaseSummary caseSummary = mock();

        final StartEventResponse startEventResponse = mock();

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

        final Map<String, Object> caseData = Map.of(
                PA_ADOPTED_IN, YES);
        when(caseDetails.getData())
                .thenReturn(caseData);

        when(caseEventsApiMock.findEventDetailsForCase(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        final boolean actual = dtspb5472RollbackMigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
        verify(caseEventsApiMock).findEventDetailsForCase(
                userBearer,
                s2sBearer,
                userId,
                jurisdiction,
                caseType,
                caseId.toString());
    }

    @Test
    void shouldMigrateCasePaAdoptedInDataMatchingCaseEventReturnsTrue() {
        final CaseSummary caseSummary = mock();

        final StartEventResponse startEventResponse = mock();

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

        final Map<String, Object> caseData = Map.of(
            PA_ADOPTED_IN, YES);
        when(caseDetails.getData())
                .thenReturn(caseData);

        final CaseEventDetail caseEventDetail = mock();
        when(caseEventDetail.getId())
                .thenReturn(MIGRATION_EVENT);
        when(caseEventDetail.getDescription())
                .thenReturn(Dtspb5472MigrationHandler.MIGRATION_DESCRIPTION);
        when(caseEventsApiMock.findEventDetailsForCase(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(caseEventDetail));

        final boolean actual = dtspb5472RollbackMigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(true));
        verify(caseEventsApiMock).findEventDetailsForCase(
                userBearer,
                s2sBearer,
                userId,
                jurisdiction,
                caseType,
                caseId.toString());
    }

    @Test
    void migrateShouldShortCircuitOnDryRun() {
        when(dtspb5472ConfigMock.isDryRun())
                .thenReturn(true);

        final CaseSummary caseSummary = mock();

        final StartEventResponse startEventResponse = mock();
        final CaseDetails caseDetails = mock();
        final Map<String, Object> caseData = new HashMap<>();
        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);
        when(caseDetails.getData())
                .thenReturn(caseData);

        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();

        final MigrationEvent migrationEvent = new MigrationEvent(caseSummary, startEventResponse, userToken, s2sToken);
        final boolean actual = dtspb5472RollbackMigrationHandler.migrate(migrationEvent);

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
        when(startEventResponse.getCaseDetails())
                .thenReturn(caseDetails);
        when(caseDetails.getData())
                .thenReturn(caseData);

        final UserDetails userDetails = mock();
        final UserToken userToken = mock();
        when(userToken.userDetails())
                .thenReturn(userDetails);
        final S2sToken s2sToken = mock();

        final MigrationEvent migrationEvent = new MigrationEvent(caseSummary, startEventResponse, userToken, s2sToken);
        final boolean actual = dtspb5472RollbackMigrationHandler.migrate(migrationEvent);

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

        final Map<String, Object> caseData = new HashMap<>(Map.of(
            PA_RELATIONSHIP_TO_DECEASED, PA_CHILD,
            SOL_RELATIONSHIP_TO_DECEASED, SOL_CHILD,
            PA_ADOPTED_IN, YES
        ));
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

        final boolean actual = dtspb5472RollbackMigrationHandler.migrate(migrationEvent);

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

        assertThat(migratedData, hasKey("migrationCallbackMetadata"));
        final Object callbackMetadataObj = migratedData.get("migrationCallbackMetadata");
        if (!(callbackMetadataObj instanceof String)) {
            fail("Metadata object is not a String");
        }
        final String callbackMetadata = (String) callbackMetadataObj;
        final JSONObject callbackMetadataJson = new JSONObject(callbackMetadata);

        assertThat(callbackMetadataJson, jsonHasString("migrationId", ROLLBACK_ID));

        assertAll(
            () -> assertThat(migratedData, hasKey(PA_RELATIONSHIP_TO_DECEASED)),
            () -> assertThat(migratedData, hasKey(SOL_RELATIONSHIP_TO_DECEASED)));
        final Object paRelationshipToDeceased = migratedData.get(PA_RELATIONSHIP_TO_DECEASED);
        final Object solRelationshipToDeceased = migratedData.get(SOL_RELATIONSHIP_TO_DECEASED);

        assertAll(
            () -> assertThat(paRelationshipToDeceased, equalTo(PA_ADOPTED_CHILD)),
            () -> assertThat(solRelationshipToDeceased, equalTo(SOL_ADOPTED_CHILD)));
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
