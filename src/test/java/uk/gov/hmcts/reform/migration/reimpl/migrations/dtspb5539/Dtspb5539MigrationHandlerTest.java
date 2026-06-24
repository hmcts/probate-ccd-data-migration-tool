package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5539;

import feign.FeignException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Dtspb5539MigrationHandlerTest {

    private static final Long CASE_REFERENCE = 1234567890123456L;
    private static final String USER_ID = "user-id";
    private static final String USER_TOKEN = "user-token";
    private static final String S2S_TOKEN = "s2s-token";

    private static final String JURISDICTION = "PROBATE";
    private static final String CASE_TYPE_ID = "GrantOfRepresentation";
    private static final String EVENT_ID = "boHistoryCorrection";
    private static final String EVENT_TOKEN = "event-token";
    private static final String HMCTS_ID = "BARS";

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    @Mock
    private ElasticSearchHandler elasticSearchHandler;

    @Mock
    private Dtspb5539ElasticQueries elasticQueries;

    @Mock
    private Dtspb5539Config config;

    @Mock
    private ReimplConfig commonConfig;

    private UserToken userToken;
    private S2sToken s2sToken;
    private Dtspb5539MigrationHandler dtspb5539migrationHandler;

    private AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        dtspb5539migrationHandler = new Dtspb5539MigrationHandler(
            coreCaseDataApi,
            elasticSearchHandler,
            elasticQueries,
            config,
            commonConfig
        );

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getId()).thenReturn(USER_ID);

        userToken = mock(UserToken.class);
        when(userToken.userDetails()).thenReturn(userDetails);
        when(userToken.getBearerToken()).thenReturn(USER_TOKEN);

        s2sToken = mock(S2sToken.class);
        when(s2sToken.s2sToken()).thenReturn(S2S_TOKEN);
    }

    @AfterEach
    void tearDown() {
        try {
            closeableMocks.close();
        } catch (Exception e) {
            // Nothing to do
        }
    }

    //getCandidateCases - Tess for Get Candidate Cases Function
    /// 1. Should Return the Found Cases and Handle for all 4 case types
    @Test
    void shouldReturnCandidateCasesFoundByElasticSearch() {

        CaseSummary case1 =
            new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION);
        CaseSummary case2 =
            new CaseSummary(2L, CaseType.CAVEAT);
        CaseSummary case3 =
            new CaseSummary(3L, CaseType.WILL_LODGEMENT);
        CaseSummary case4 =
            new CaseSummary(4L, CaseType.STANDING_SEARCH);

        when(config.getCaseTypes()).thenReturn(List.of(
            CaseType.GRANT_OF_REPRESENTATION,
            CaseType.CAVEAT,
            CaseType.WILL_LODGEMENT,
            CaseType.STANDING_SEARCH
        ));

        when(elasticSearchHandler.searchCases(
            anyString(),
            eq(userToken),
            eq(s2sToken),
            eq(CaseType.GRANT_OF_REPRESENTATION),
            any()))
            .thenReturn(Set.of(case1));

        when(elasticSearchHandler.searchCases(
            anyString(),
            eq(userToken),
            eq(s2sToken),
            eq(CaseType.CAVEAT),
            any()))
            .thenReturn(Set.of(case2));

        when(elasticSearchHandler.searchCases(
            anyString(),
            eq(userToken),
            eq(s2sToken),
            eq(CaseType.WILL_LODGEMENT),
            any()))
            .thenReturn(Set.of(case3));

        when(elasticSearchHandler.searchCases(
            anyString(),
            eq(userToken),
            eq(s2sToken),
            eq(CaseType.STANDING_SEARCH),
            any()))
            .thenReturn(Set.of(case4));

        Set<CaseSummary> result =
            dtspb5539migrationHandler.getCandidateCases(userToken, s2sToken);

        assertThat(result)
            .containsExactlyInAnyOrder(case1, case2, case3, case4);
    }

    //getCandidateCases - Tess for Get Candidate Cases Function
    /// 2. Empty set should be returned when no cases are found
    @Test
    void shouldReturnEmptySetWhenNoCandidateCasesFound() {

        when(config.getCaseTypes())
            .thenReturn(
                List.of(CaseType.GRANT_OF_REPRESENTATION)
            );

        when(elasticSearchHandler.searchCases(
            anyString(),
            eq(userToken),
            eq(s2sToken),
            eq(CaseType.GRANT_OF_REPRESENTATION),
            any()))
            .thenReturn(Set.of());

        final Set<CaseSummary> result =
            dtspb5539migrationHandler.getCandidateCases(
                userToken,
                s2sToken
            );

        assertThat(result).isEmpty();
    }


    //startEventForCase - Tests
    /// 1. Ensuring the CCD Api for the Start Event is called for happy path
    @Test
    void shouldStartBoHistoryCorrectionEvent() {

        CaseSummary caseSummary = createCaseSummary();

        StartEventResponse startEventResponse = mock();

        when(coreCaseDataApi.startEventForCaseWorker(
            USER_TOKEN,
            S2S_TOKEN,
            USER_ID,
            JURISDICTION,
            CASE_TYPE_ID,
            CASE_REFERENCE.toString(),
            EVENT_ID))
            .thenReturn(startEventResponse);

        MigrationEvent result = dtspb5539migrationHandler.startEventForCase(
            caseSummary,
            userToken,
            s2sToken
        );

        assertAll(
            () -> assertSame(caseSummary, result.caseSummary()),
            () -> assertSame(userToken, result.userToken()),
            () -> assertSame(s2sToken, result.s2sToken()),
            () -> assertSame(startEventResponse, result.startEventResponse())
        );

        verify(coreCaseDataApi).startEventForCaseWorker(
            USER_TOKEN,
            S2S_TOKEN,
            USER_ID,
            JURISDICTION,
            CASE_TYPE_ID,
            CASE_REFERENCE.toString(),
            EVENT_ID
        );
    }

    //startEventForCase - Tests
    /// 2. Ensuring the error is returned and propogated when start event is failed
    @Test
    void shouldPropagateExceptionWhenStartEventFails() {

        CaseSummary caseSummary = createCaseSummary();

        when(coreCaseDataApi.startEventForCaseWorker(
            USER_TOKEN,
            S2S_TOKEN,
            USER_ID,
            JURISDICTION,
            CASE_TYPE_ID,
            CASE_REFERENCE.toString(),
            EVENT_ID))
            .thenThrow(new RuntimeException("CCD unavailable"));

        assertThrows(
            RuntimeException.class,
            () -> dtspb5539migrationHandler.startEventForCase(
                caseSummary,
                userToken,
                s2sToken
            )
        );
    }


    // shouldMigrateCase - Tests
    /// 1. When Case Details is empty Should Throw an Error
    @Test
    void shouldMigrateCaseNullDetailsThrows() {
        final MigrationEvent migrationEvent = mock();

        final CaseSummary caseSummary = mock();
        final StartEventResponse startEventResponse = mock();

        when(migrationEvent.caseSummary())
            .thenReturn(caseSummary);

        when(migrationEvent.startEventResponse())
            .thenReturn(startEventResponse);

        when(startEventResponse.getCaseDetails())
            .thenReturn(null);

        assertThrows(
            Dtspb5539MigrationHandler.Dtspb5539MigrationException.class,
            () -> dtspb5539migrationHandler.shouldMigrateCase(migrationEvent)
        );
    }

    // shouldMigrateCase - Tests
    /// 2. When Case Details are present Should continue the flow
    @Test
    void shouldReturnTrueWhenCaseDetailsPresent() {

        Map<String, Object> data = new HashMap<>();

        CaseDetails caseDetails =
            createCaseDetails(data);

        StartEventResponse startEventResponse =
            createStartEventResponse(caseDetails);

        MigrationEvent migrationEvent =
            createMigrationEvent(startEventResponse);

        boolean result =
            dtspb5539migrationHandler.shouldMigrateCase(
                migrationEvent
            );

        assertTrue(result);
    }

    //Migrate - Tests
    /// 1. Should not run the API calls to core case when dry run is true and should return true
    @Test
    void shouldReturnTrueWhenDryRunEnabled() {

        when(commonConfig.isDryRun())
            .thenReturn(true);

        when(config.getHmctsId())
            .thenReturn(HMCTS_ID);

        Map<String, Object> data = new HashMap<>();

        CaseDetails caseDetails =
            createCaseDetails(data);

        StartEventResponse startEventResponse =
            createStartEventResponse(caseDetails);

        MigrationEvent migrationEvent =
            createMigrationEvent(startEventResponse);

        boolean result =
            dtspb5539migrationHandler.migrate(
                migrationEvent
            );

        assertTrue(result);

        assertThat(data)
            .containsKey("migrationCallbackMetadata");

        String metadata =
            (String) data.get("migrationCallbackMetadata");

        assertThat(metadata)
            .contains("DTSPB-5539");

        verifyNoInteractions(coreCaseDataApi);
    }

    //Migrate - Tests
    /// 2. Should run the two API calls to core case when dry run is true and should return true
    @Test
    void shouldSubmitMigrationAndSupplementaryDataSuccessfully() {

        when(commonConfig.isDryRun())
            .thenReturn(false);

        when(config.getHmctsId())
            .thenReturn(HMCTS_ID);

        Map<String, Object> data = new HashMap<>();

        CaseDetails caseDetails =
            createCaseDetails(data);

        StartEventResponse startEventResponse =
            createStartEventResponse(caseDetails);

        MigrationEvent migrationEvent =
            createMigrationEvent(startEventResponse);

        when(coreCaseDataApi.submitEventForCaseWorker(
            eq(USER_TOKEN),
            eq(S2S_TOKEN),
            eq(USER_ID),
            eq(JURISDICTION),
            eq(CASE_TYPE_ID),
            eq(CASE_REFERENCE.toString()),
            eq(true),
            any(CaseDataContent.class)
        )).thenReturn(caseDetails);

        boolean result =
            dtspb5539migrationHandler.migrate(migrationEvent);

        assertTrue(result);

        verify(coreCaseDataApi).submitEventForCaseWorker(
            eq(USER_TOKEN),
            eq(S2S_TOKEN),
            eq(USER_ID),
            eq(JURISDICTION),
            eq(CASE_TYPE_ID),
            eq(CASE_REFERENCE.toString()),
            eq(true),
            any(CaseDataContent.class)
        );

        verify(coreCaseDataApi).submitSupplementaryData(
            eq(USER_TOKEN),
            eq(S2S_TOKEN),
            eq(CASE_REFERENCE.toString()),
            anyMap()
        );

        assertThat(data)
            .containsKey("migrationCallbackMetadata");

        String metadata =
            (String) data.get("migrationCallbackMetadata");

        assertThat(metadata)
            .contains("DTSPB-5539");
    }

    //Migrate - Tests
    ///3. If Migrate event returns null, the supplementary data is never submitted
    @Test
    void shouldReturnFalseWhenEventSubmissionReturnsNull() {

        when(commonConfig.isDryRun())
            .thenReturn(false);

        when(config.getHmctsId())
            .thenReturn(HMCTS_ID);

        CaseDetails caseDetails =
            createCaseDetails(new HashMap<>());

        StartEventResponse startEventResponse =
            createStartEventResponse(caseDetails);

        MigrationEvent migrationEvent =
            createMigrationEvent(startEventResponse);

        when(coreCaseDataApi.submitEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq(true),
            any(CaseDataContent.class)
        )).thenReturn(null);

        boolean result =
            dtspb5539migrationHandler.migrate(migrationEvent);

        assertFalse(result);

        verify(coreCaseDataApi, never())
            .submitSupplementaryData(
                anyString(),
                anyString(),
                anyString(),
                anyMap()
            );
    }

    //Migrate - Tests
    /// 4. Should return false when supplementary data update fails and should throw a feign exception
    @Test
    void shouldReturnFalseWhenSupplementaryDataUpdateFails() {

        when(commonConfig.isDryRun())
            .thenReturn(false);

        when(config.getHmctsId())
            .thenReturn(HMCTS_ID);

        CaseDetails caseDetails =
            createCaseDetails(new HashMap<>());

        StartEventResponse startEventResponse =
            createStartEventResponse(caseDetails);

        MigrationEvent migrationEvent =
            createMigrationEvent(startEventResponse);

        when(coreCaseDataApi.submitEventForCaseWorker(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            eq(true),
            any(CaseDataContent.class)
        )).thenReturn(caseDetails);

        FeignException feignException =
            mock(FeignException.class);

        doThrow(feignException)
            .when(coreCaseDataApi)
            .submitSupplementaryData(
                anyString(),
                anyString(),
                anyString(),
                anyMap()
            );

        boolean result =
            dtspb5539migrationHandler.migrate(migrationEvent);

        assertFalse(result);
    }

    private CaseSummary createCaseSummary() {
        return new CaseSummary(
            CASE_REFERENCE,
            CaseType.GRANT_OF_REPRESENTATION
        );
    }

    private MigrationEvent createMigrationEvent(
        StartEventResponse startEventResponse
    ) {
        return new MigrationEvent(
            createCaseSummary(),
            startEventResponse,
            userToken,
            s2sToken
        );
    }

    private CaseDetails createCaseDetails(Map<String, Object> data) {
        return CaseDetails.builder()
            .id(CASE_REFERENCE)
            .jurisdiction(JURISDICTION)
            .caseTypeId(CASE_TYPE_ID)
            .data(data)
            .build();
    }

    private StartEventResponse createStartEventResponse(
        CaseDetails caseDetails
    ) {
        StartEventResponse startEventResponse =
            mock(StartEventResponse.class);

        when(startEventResponse.getCaseDetails())
            .thenReturn(caseDetails);

        when(startEventResponse.getEventId())
            .thenReturn(EVENT_ID);

        when(startEventResponse.getToken())
            .thenReturn(EVENT_TOKEN);

        return startEventResponse;
    }
}
