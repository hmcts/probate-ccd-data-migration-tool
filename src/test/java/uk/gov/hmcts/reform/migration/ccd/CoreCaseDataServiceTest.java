package uk.gov.hmcts.reform.migration.ccd;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.domain.common.AuditEvent;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.service.AuditEventService;
import uk.gov.hmcts.reform.migration.service.DataMigrationService;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CoreCaseDataServiceTest {

    private static final String EVENT_ID = "migrateCase";
    private static final String CASE_TYPE = "CARE_SUPERVISION_EPO";
    private static final String CASE_ID = "123456789";
    private static final String USER_ID = "30";
    private static final String AUTH_TOKEN = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJubGJoN";
    private static final String EVENT_TOKEN = "Bearer aaaadsadsasawewewewew";
    private static final String EVENT_SUMMARY = "Migrate Case";
    private static final String EVENT_DESC = "Migrate Case";
    private static final List<String> createCaseFromBulkScanEventEvent = Arrays.asList("createCaseFromBulkScanEvent");


    @InjectMocks
    private CoreCaseDataService underTest;

    @Mock
    private CoreCaseDataApi coreCaseDataApi;

    @Mock
    private DataMigrationService<Map<String, Object>> dataMigrationService;

    @Mock
    private IdamClient idamClient;

    @Mock
    private AuthTokenGenerator authTokenGenerator;

    @Mock
    private AuditEventService auditEventService;

    @Before
    public void setUp() {
    }

    @Test
    public void shouldUpdateApplicationSubmittedDate() {
        // given
        UserDetails userDetails = UserDetails.builder()
            .id("30")
            .email("test@test.com")
            .forename("Test")
            .surname("Surname")
            .build();

        CaseDetails caseDetails = createCaseDetailsPreMigration(CASE_ID);
        setupMocks(userDetails, caseDetails.getData());

        //when
        CaseDetails update = underTest.update(AUTH_TOKEN, EVENT_ID, EVENT_SUMMARY, EVENT_DESC, CASE_TYPE, caseDetails);
        //then
        assertEquals(update.getData().get("applicationSubmittedDate"), LocalDate.now().toString());
    }

    private CaseDetails createCaseDetailsPreMigration(String id) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("applicationSubmittedDate", null);
        return CaseDetails.builder()
            .id(Long.valueOf(id))
            .data(data)
            .build();
    }

    private CaseDetails createCaseDetailsPostMigration(String id) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        String date = LocalDate.now().toString();
        data.put("applicationSubmittedDate", date);
        return CaseDetails.builder()
            .id(Long.valueOf(id))
            .data(data)
            .build();
    }

    private void setupMocks(UserDetails userDetails, Map<String, Object> data) {
        when(idamClient.getUserDetails(AUTH_TOKEN)).thenReturn(userDetails);

        when(authTokenGenerator.generate()).thenReturn(AUTH_TOKEN);

        CaseDetails caseDetails = CaseDetails.builder()
            .id(123456789L)
            .data(data)
            .build();

        StartEventResponse startEventResponse = StartEventResponse.builder()
            .eventId(EVENT_ID)
            .token(EVENT_TOKEN)
            .caseDetails(caseDetails)
            .build();

        when(dataMigrationService.migrate(any(), any(), any(), any()))
            .thenReturn(createCaseDetailsPostMigration(CASE_ID).getData());

        AuditEvent auditEvent = AuditEvent.builder()
            .id("smsm")
            .userFirstName("bob")
            .build();

        when(coreCaseDataApi.startEventForCaseWorker(AUTH_TOKEN, AUTH_TOKEN, "30",
                                                     null, CASE_TYPE, CASE_ID, EVENT_ID
        ))
            .thenReturn(startEventResponse);

        CaseDataContent caseDataContent = CaseDataContent.builder()
            .event(Event.builder()
                       .id(EVENT_ID)
                       .description(EVENT_DESC)
                       .summary(EVENT_SUMMARY)
                       .build())
            .eventToken(EVENT_TOKEN)
            .data(createCaseDetailsPostMigration(CASE_ID).getData())
            .ignoreWarning(false)
            .build();


        when(coreCaseDataApi.submitEventForCaseWorker(AUTH_TOKEN, AUTH_TOKEN, USER_ID, null,
                                                      CASE_TYPE, CASE_ID, true, caseDataContent))
            .thenReturn(createCaseDetailsPostMigration(CASE_ID));
    }
}
