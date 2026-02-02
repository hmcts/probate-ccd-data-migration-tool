package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064;

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
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064.Dtspb5064MigrationHandler.APPLICANT_ORGANISATION_POLICY;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064.Dtspb5064MigrationHandler.CAVEAT;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064.Dtspb5064MigrationHandler.GRANT_OF_REPRESENTATION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064.Dtspb5064MigrationHandler.JURISDICTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064.Dtspb5064MigrationHandler.MIGRATION_DESCRIPTION;
import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064.Dtspb5064MigrationHandler.MIGRATION_SUMMARY;

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
    void getCandidateCasesCallsElasticSearchTwice() {
        final UserToken userToken = mock();
        final S2sToken s2sToken = mock();

        final CaseSummary gorCase = new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION);
        final CaseSummary caveatCase = new CaseSummary(2L, CaseType.CAVEAT);

        when(elasticSearchHandlerMock.searchCases(
                        any(),
                        any(),
                        any(),
                        eq(CaseType.GRANT_OF_REPRESENTATION),
                        any()))
                .thenReturn(Set.of(gorCase));
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
                        any(),
                        eq(userToken),
                        eq(s2sToken),
                        eq(CaseType.GRANT_OF_REPRESENTATION),
                        any()),
                () -> verify(elasticSearchHandlerMock).searchCases(
                        any(),
                        eq(userToken),
                        eq(s2sToken),
                        eq(CaseType.CAVEAT),
                        any()),
                () -> assertThat(candidateCases, hasSize(2)),
                () -> assertThat(candidateCases, containsInAnyOrder(gorCase, caveatCase)));
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
                        GRANT_OF_REPRESENTATION,
                        caseId.toString(),
                        "boHistoryCorrection"));
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
    void shouldMigrateCaseNoAppOrgPolicyTrue() {
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

        final boolean actual = dtspb5064MigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(true));
    }

    @Test
    void shouldMigrateCaseWithAppOrgPolicyFalse() {
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

        final Map<String, Object> caseData = Map.of(
                APPLICANT_ORGANISATION_POLICY, "");
        when(caseDetails.getData())
                .thenReturn(caseData);

        final boolean actual = dtspb5064MigrationHandler.shouldMigrateCase(migrationEvent);

        assertThat(actual, equalTo(false));
    }

    @Test
    void migrateCaseAddsAppOrgPolicyAndCallsCcdSubmit() {
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

        final boolean actual = dtspb5064MigrationHandler.migrate(migrationEvent);

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

        assertThat(migratedData, hasKey(APPLICANT_ORGANISATION_POLICY));
        final Object policyObj = migratedData.get(APPLICANT_ORGANISATION_POLICY);
        if (!(policyObj instanceof Map)) {
            fail("Policy object is not a Map");
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> policy = (Map<String, Object>) policyObj;

        assertAll(
                () -> assertThat(policy, hasKey("Organisation")),
                () -> assertThat(policy, hasKey("OrgPolicyReference")),
                () -> assertThat(policy, hasKey("OrgPolicyCaseAssignedRole")));

        final Object organisationObj = policy.get("Organisation");
        if (!(organisationObj instanceof Map)) {
            fail("organisationObj is not a Map");
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> organisation = (Map<String, Object>) organisationObj;

        assertAll(
                () -> assertThat(organisation, hasKey("OrganisationId")),
                () -> assertThat(organisation, hasKey("OrganisationName")));
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
