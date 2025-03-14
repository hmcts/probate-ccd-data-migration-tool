package uk.gov.hmcts.reform.migration.ccd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.model.Dtspb4583Dates;
import uk.gov.hmcts.reform.migration.service.dtspb4583.Dtspb4583DataService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreCaseDataServiceTest {

    private static final String EVENT_ID = "migrateCase";
    private static final String CASE_TYPE = "GrantOfRepresentation";
    private static final String CASE_JURISDICTION = "PROBATE";
    private static final String USER_ID = "30";
    private static final UserDetails USER_DETAILS = UserDetails.builder().id(USER_ID).build();
    private static final String AUTH_TOKEN = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJubGJoN";
    private static final String EVENT_TOKEN = "Bearer aaaadsadsasawewewewew";
    private static final String EVENT_SUMMARY = "Migrate Case";
    private static final String EVENT_DESC = "Migrate Case";
    private static final String APPL_SUBMIT_DATE = "applicationSubmittedDate";

    // Service Mocks
    private IdamClient idamClientMock;
    private AuthTokenGenerator authTokenGeneratorMock;
    private CoreCaseDataApi coreCaseDataApiMock;
    private Dtspb4583DataService dtspb4583DataServiceMock;

    // Answer implementations
    private StartEventAnswer startEventAnswer;
    private SubmitEventAnswer submitEventAnswer;

    private CoreCaseDataService underTest;

    @BeforeEach
    void setUp() {
        idamClientMock = mock(IdamClient.class);
        authTokenGeneratorMock = mock(AuthTokenGenerator.class);
        coreCaseDataApiMock = mock(CoreCaseDataApi.class);
        dtspb4583DataServiceMock = mock(Dtspb4583DataService.class);

        underTest = new CoreCaseDataService(
            idamClientMock,
            authTokenGeneratorMock,
            coreCaseDataApiMock,
            dtspb4583DataServiceMock);

        startEventAnswer = new StartEventAnswer();
        submitEventAnswer = new SubmitEventAnswer();

        when(idamClientMock.getUserDetails(AUTH_TOKEN)).thenReturn(USER_DETAILS);
        when(authTokenGeneratorMock.generate()).thenReturn(EVENT_TOKEN);
        when(coreCaseDataApiMock.startEventForCaseWorker(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )).then(startEventAnswer);
        when(coreCaseDataApiMock.submitEventForCaseWorker(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any()
        )).then(submitEventAnswer);
    }

    @Test
    void testWillMigrateMatchingExpectedCase() {
        final Long caseId = 1L;
        final String dateBefore = "incorrect";
        final String dateAfter = "correct";
        final Map<String, Object> caseData = Map.of(
            "applicationSubmittedDate", dateBefore
        );
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.of(new Dtspb4583Dates(dateBefore, dateAfter));
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.update(AUTH_TOKEN, EVENT_ID, EVENT_SUMMARY, EVENT_DESC, CASE_TYPE, before);

        assertNotNull(actual, "Expected case to be updated");
        assertEquals(dateAfter, actual.getData().get(APPL_SUBMIT_DATE), "Expected submit date to be updated");
    }

    @Test
    void testWillNotMigrateNonMatchingExpectedCase() {
        final Long caseId = 1L;
        final String dateBefore = "incorrect";
        final String dateActual = "actual";
        final String dateAfter = "correct";
        final Map<String, Object> caseData = Map.of(
            "applicationSubmittedDate", dateActual
        );
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.of(new Dtspb4583Dates(dateBefore, dateAfter));
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.update(AUTH_TOKEN, EVENT_ID, EVENT_SUMMARY, EVENT_DESC, CASE_TYPE, before);

        assertNull(actual, "Expected case not to be updated");
    }

    @Test
    void testWillNotMigrateExpectedCaseWithUnexpectedType() {
        final Long caseId = 1L;
        final String dateBefore = "incorrect";
        final Long dateActual = 1L;
        final String dateAfter = "correct";
        final Map<String, Object> caseData = Map.of(
            "applicationSubmittedDate", dateActual
        );
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.of(new Dtspb4583Dates(dateBefore, dateAfter));
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.update(AUTH_TOKEN, EVENT_ID, EVENT_SUMMARY, EVENT_DESC, CASE_TYPE, before);

        assertNull(actual, "Expected case not to be updated");
    }

    @Test
    void testWillNotMigrateExpectedCaseWithoutAppSubmDate() {
        final Long caseId = 1L;
        final String dateBefore = "incorrect";
        final String dateAfter = "correct";
        final Map<String, Object> caseData = Map.of();
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.of(new Dtspb4583Dates(dateBefore, dateAfter));
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.update(AUTH_TOKEN, EVENT_ID, EVENT_SUMMARY, EVENT_DESC, CASE_TYPE, before);

        assertNull(actual, "Expected case not to be updated");
    }

    @Test
    void testWillNotMigrateUnexpectedCase() {
        final Long caseId = 1L;
        final String dateBefore = "incorrect";
        final Map<String, Object> caseData = Map.of(
            "applicationSubmittedDate", dateBefore
        );
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.empty();
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.update(AUTH_TOKEN, EVENT_ID, EVENT_SUMMARY, EVENT_DESC, CASE_TYPE, before);

        assertNull(actual, "Expected case not to be updated");
    }

    @Test
    void testWillRollbackMatchingExpectedCase() {
        final Long caseId = 1L;
        final String dateBefore = "incorrect";
        final String dateAfter = "correct";
        final Map<String, Object> caseData = Map.of(
            "applicationSubmittedDate", dateAfter
        );
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.of(new Dtspb4583Dates(dateBefore, dateAfter));
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.rollback(
                AUTH_TOKEN,
                EVENT_ID,
                EVENT_SUMMARY,
                EVENT_DESC,
                CASE_TYPE,
                before);

        assertNotNull(actual, "Expected case to be rolled back");
        assertEquals(
                dateBefore,
                actual.getData().get(APPL_SUBMIT_DATE),
                "Expected submit date to be rolled back");
    }

    @Test
    void testWillNotRollbackNonMatchingExpectedCase() {
        final Long caseId = 1L;
        final String dateBefore = "incorrect";
        final String dateActual = "actual";
        final String dateAfter = "correct";
        final Map<String, Object> caseData = Map.of(
            "applicationSubmittedDate", dateActual
        );
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.of(new Dtspb4583Dates(dateBefore, dateAfter));
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.rollback(AUTH_TOKEN,
                EVENT_ID,
                EVENT_SUMMARY,
                EVENT_DESC,
                CASE_TYPE,
                before);

        assertNull(actual, "Expected case not to be rolled back");
    }

    @Test
    void testWillNotRollbackExpectedCaseWithUnexpectedType() {
        final Long caseId = 1L;
        final String dateBefore = "incorrect";
        final Long dateActual = 1L;
        final String dateAfter = "correct";
        final Map<String, Object> caseData = Map.of(
            "applicationSubmittedDate", dateActual
        );
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.of(new Dtspb4583Dates(dateBefore, dateAfter));
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.rollback(
                AUTH_TOKEN,
                EVENT_ID,
                EVENT_SUMMARY,
                EVENT_DESC,
                CASE_TYPE,
                before);

        assertNull(actual, "Expected case not to be rolled back");
    }

    @Test
    void testWillNotRollbackExpectedCaseWithoutAppSubmDate() {
        final Long caseId = 1L;
        final String dateBefore = "incorrect";
        final String dateAfter = "correct";
        final Map<String, Object> caseData = Map.of();
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.of(new Dtspb4583Dates(dateBefore, dateAfter));
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.rollback(
                AUTH_TOKEN,
                EVENT_ID,
                EVENT_SUMMARY,
                EVENT_DESC,
                CASE_TYPE,
                before);

        assertNull(actual, "Expected case not to be rolled back");
    }

    @Test
    void testWillNotRollbackUnexpectedCase() {
        final Long caseId = 1L;
        final String dateAfter = "correct";
        final Map<String, Object> caseData = Map.of(
            "applicationSubmittedDate", dateAfter
        );
        final CaseDetails before = CaseDetails.builder()
            .id(caseId)
            .jurisdiction(CASE_JURISDICTION)
            .caseTypeId(CASE_TYPE)
            .build();

        final Optional<Dtspb4583Dates> expectedDates = Optional.empty();
        when(dtspb4583DataServiceMock.get(caseId))
            .thenReturn(expectedDates);

        startEventAnswer.setWantedCaseData(caseData);

        final CaseDetails actual = underTest.rollback(
                AUTH_TOKEN,
                EVENT_ID,
                EVENT_SUMMARY,
                EVENT_DESC,
                CASE_TYPE,
                before);

        assertNull(actual, "Expected case not to be rolled back");
    }

    static class StartEventAnswer implements Answer<StartEventResponse> {
        private Map<String, Object> wantedCaseData = new HashMap<>();

        @Override
        public StartEventResponse answer(InvocationOnMock invocation) {
            final String jurisdiction = invocation.getArgument(3);
            final String caseType = invocation.getArgument(4);
            final String caseIdStr = invocation.getArgument(5);
            final Long caseId = Long.parseLong(caseIdStr);

            final CaseDetails caseDetails = CaseDetails.builder()
                .id(caseId)
                .jurisdiction(jurisdiction)
                .caseTypeId(caseType)
                .data(copyMap(wantedCaseData))
                .build();
            return StartEventResponse.builder()
                .eventId(EVENT_ID)
                .token(EVENT_TOKEN)
                .caseDetails(caseDetails)
                .build();
        }

        public void setWantedCaseData(Map<String, Object> wantedCaseData) {
            this.wantedCaseData = copyMap(wantedCaseData);
        }

        /* This is pretty fragile - it assumes any non-Map value is immutable, and
         * that any Map value is a Map<String, Object> . Violate those at your own
         * risk.
         */
        private Map<String, Object> copyMap(final Map<String, Object> input) {
            if (input == null) {
                throw new IllegalArgumentException("no null maps");
            }
            final Map<String, Object> result = new HashMap<>();
            for (final var entry : input.entrySet()) {
                final String key = entry.getKey();
                final Object value = entry.getValue();
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked") final Object newValue = copyMap((Map<String, Object>) value);
                    result.put(key, newValue);
                } else {
                    result.put(key, value);
                }
            }
            return result;
        }
    }

    static class SubmitEventAnswer implements Answer<CaseDetails> {
        @Override
        public CaseDetails answer(InvocationOnMock invocation) {
            final String jurisdiction = invocation.getArgument(3);
            final String caseType = invocation.getArgument(4);
            final String caseIdStr = invocation.getArgument(5);
            final Long caseId = Long.parseLong(caseIdStr);
            final CaseDataContent caseDataContent = invocation.getArgument(7);

            @SuppressWarnings("unchecked")
            final Map<String, Object> caseData = (Map<String, Object>) caseDataContent.getData();

            return CaseDetails.builder()
                .id(caseId)
                .jurisdiction(jurisdiction)
                .caseTypeId(caseType)
                .data(caseData)
                .build();
        }
    }
}
