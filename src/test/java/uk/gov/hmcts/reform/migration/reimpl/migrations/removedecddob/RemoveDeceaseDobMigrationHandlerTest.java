package uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.migration.reimpl.TestUtils.jsonHasString;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob.RemoveDeceasedDobMigrationHandler.GRANT_OF_REPRESENTATION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob.RemoveDeceasedDobMigrationHandler.JURISDICTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob.RemoveDeceasedDobMigrationHandler.MIGRATION_DESCRIPTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob.RemoveDeceasedDobMigrationHandler.MIGRATION_ID;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob.RemoveDeceasedDobMigrationHandler.MIGRATION_SUMMARY;

public class RemoveDeceaseDobMigrationHandlerTest {
    @Mock
    CoreCaseDataApi coreCaseDataApiMock;
    @Mock
    RemoveDeceasedDobConfig removeDeceasedDobConfig;
    @Mock
    ReimplConfig reimplConfigMock;

    RemoveDeceasedDobMigrationHandler removeDeceasedDobMigrationHandler;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        removeDeceasedDobMigrationHandler = new RemoveDeceasedDobMigrationHandler(
            coreCaseDataApiMock,
            reimplConfigMock,
            removeDeceasedDobConfig);
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
    void startEvent() {
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

        final MigrationEvent actual = removeDeceasedDobMigrationHandler.startEventForCase(
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
    void shouldMigrateCaseReturnsTrue() {
        final boolean firstActual = removeDeceasedDobMigrationHandler.shouldMigrateCase(null);
        final boolean secondActual = removeDeceasedDobMigrationHandler.shouldMigrateCase(mock());

        assertAll(
            () -> assertTrue(firstActual),
            () -> assertTrue(secondActual));
    }

    @Test
    void migrateCaseSetsMigrationMetadata() {
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

        final boolean actual = removeDeceasedDobMigrationHandler.migrate(migrationEvent);

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
            fail("Migrated object is not a map");
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> migratedData = (Map<String, Object>) migratedObj;

        assertThat(migratedData, hasKey("migrationCallbackMetadata"));
        final Object metadata = migratedData.get("migrationCallbackMetadata");
        if (!(metadata instanceof String)) {
            fail("Migration metadata is not a String");
        }
        @SuppressWarnings("unchecked")
        final String metadataData = (String) metadata;
        final JSONObject metadataJson = new JSONObject(metadataData);
        assertThat(metadataJson, jsonHasString("migrationId", MIGRATION_ID));
    }
}
