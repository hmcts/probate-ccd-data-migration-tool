package uk.gov.hmcts.reform.migration.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.domain.common.AuditEvent;
import uk.gov.hmcts.reform.domain.common.AuditEventsResponse;
import uk.gov.hmcts.reform.migration.client.CaseDataApiV2;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuditEventServiceTest {

    private static final String USER_TOKEN = "USER_TOKEN";
    private static final String SERVICE_TOKEN = "SERVICE_TOKEN";
    private static final String CASE_ID = "1111";
    private static final String SOLICITOR_EVENT = "solicitorCreateApplication";

    @Mock
    private CaseDataApiV2 mockCaseDataApi;

    @Mock
    private AuditEventsResponse auditEventsResponse;

    @InjectMocks
    private AuditEventService auditEventService;

    @Before
    public void setUp() {
        when(mockCaseDataApi.getAuditEvents(USER_TOKEN, SERVICE_TOKEN, false, CASE_ID))
            .thenReturn(auditEventsResponse);
    }

    @Test
    public void shouldGetAuditEventByName() {
        List<String> eventName = List.of(SOLICITOR_EVENT);
        AuditEvent expectedAuditEvent = AuditEvent.builder().id(SOLICITOR_EVENT).userId("123").build();
        when(mockCaseDataApi.getAuditEvents(USER_TOKEN, SERVICE_TOKEN, false, CASE_ID))
            .thenReturn(AuditEventsResponse.builder().auditEvents(List.of(expectedAuditEvent)).build());

        Optional<AuditEvent> actualAuditEvent
            = auditEventService.getLatestAuditEventByName(CASE_ID, eventName, USER_TOKEN, SERVICE_TOKEN);

        assertTrue(actualAuditEvent.isPresent());

        assertEquals(expectedAuditEvent.getId(), actualAuditEvent.get().getId());
        assertEquals(expectedAuditEvent.getUserId(), actualAuditEvent.get().getUserId());
    }

    @Test
    public void shouldReturnEmptyOptionalIfAuditEventWithNameCannotBeFound() {
        List<String> eventName = List.of(SOLICITOR_EVENT);
        AuditEvent expectedAuditEvent = AuditEvent.builder().id("abc").userId("123").build();

        when(auditEventsResponse.getAuditEvents()).thenReturn(List.of(expectedAuditEvent));

        Optional<AuditEvent> actualAuditEvent
            = auditEventService.getLatestAuditEventByName(CASE_ID, eventName, USER_TOKEN, SERVICE_TOKEN);

        assertThat(actualAuditEvent).isEmpty();
    }
}
