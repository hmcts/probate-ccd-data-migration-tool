package uk.gov.hmcts.reform.migration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.domain.common.AuditEvent;
import uk.gov.hmcts.reform.domain.common.AuditEventsResponse;
import uk.gov.hmcts.reform.migration.client.CaseDataApiV2;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditEventServiceTest {

    private static final String USER_TOKEN = "USER_TOKEN";
    private static final String SERVICE_TOKEN = "SERVICE_TOKEN";
    private static final String CASE_ID = "1111";
    private static final String EVENT = "createCaseFromBulkScanEvent";
    private static final String EVENT_NAME = "updateDraft";
    private static final String STATE_NAME = "Pending";
    private static final String STATE_BO_CASE_STOPPED = "BOCaseStopped";
    private static final String STATE_BO_CASE_STOPPED_REISSUE = "BOCaseStoppedReissue";
    private static final String STATE_CASE_PRINTED = "CasePrinted";
    private static final String STATE_DORMANT = "Dormant";
    private static final LocalDateTime LOCAL_DATE_TIME = LocalDateTime.now();

    @Mock
    private CaseDataApiV2 mockCaseDataApi;

    @Mock
    private AuditEventsResponse auditEventsResponse;

    @Mock
    private UserToken userToken;

    @Mock
    private S2sToken s2sToken;

    @InjectMocks
    private AuditEventService auditEventService;

    @BeforeEach
    void setUp() {
        when(mockCaseDataApi.getAuditEvents(USER_TOKEN, SERVICE_TOKEN, false, CASE_ID))
            .thenReturn(auditEventsResponse);
    }

    @Test
    void shouldGetAuditEventByName() {
        AuditEvent expectedAuditEvent = AuditEvent.builder().id(EVENT).userId("123").build();
        when(mockCaseDataApi.getAuditEvents(USER_TOKEN, SERVICE_TOKEN, false, CASE_ID))
            .thenReturn(AuditEventsResponse.builder().auditEvents(List.of(expectedAuditEvent)).build());

        Optional<AuditEvent> actualAuditEvent
            = auditEventService.getLatestAuditEventByName(CASE_ID, Arrays.asList("boCorrection"), USER_TOKEN,
            SERVICE_TOKEN);

        assertTrue(actualAuditEvent.isPresent());

        assertEquals(expectedAuditEvent.getId(), actualAuditEvent.get().getId());
        assertEquals(expectedAuditEvent.getUserId(), actualAuditEvent.get().getUserId());
    }

    @Test
    void shouldReturnEmptyOptionalIfAuditEventWithNameCannotBeFound() {
        List<String> eventName = List.of(EVENT);
        AuditEvent expectedAuditEvent = AuditEvent.builder().id(EVENT).userId("123").build();

        when(auditEventsResponse.getAuditEvents()).thenReturn(List.of(expectedAuditEvent));

        Optional<AuditEvent> actualAuditEvent
            = auditEventService.getLatestAuditEventByName(CASE_ID, eventName, USER_TOKEN, SERVICE_TOKEN);

        assertThat(actualAuditEvent).isEmpty();
    }

    @Test
    void shouldReturnLatestAuditEventWhenStateIsInProvidedStateList() {
        when(userToken.getBearerToken())
            .thenReturn(USER_TOKEN);
        when(s2sToken.s2sToken())
            .thenReturn(SERVICE_TOKEN);
        AuditEvent expectedAuditEvent =
            buildAuditEvent(EVENT_NAME, STATE_NAME, LOCAL_DATE_TIME);

        List<AuditEvent> auditEventList = List.of(
            buildAuditEvent(EVENT_NAME, STATE_CASE_PRINTED, LOCAL_DATE_TIME.minusMinutes(5)),
            expectedAuditEvent,
            buildAuditEvent(EVENT_NAME, STATE_BO_CASE_STOPPED_REISSUE, LOCAL_DATE_TIME.minusMinutes(2))
        );

        when(auditEventsResponse.getAuditEvents()).thenReturn(auditEventList);

        Optional<AuditEvent> actualAuditEvent =
            auditEventService.getLatestAuditEventInStateList(
                CASE_ID,
                List.of(STATE_NAME),
                userToken,
                s2sToken
            );

        assertThat(actualAuditEvent).isPresent().contains(expectedAuditEvent);
    }

    @Test
    void shouldReturnEmptyOptionalWhenLatestAuditEventStateIsNotInProvidedList() {
        when(userToken.getBearerToken())
            .thenReturn(USER_TOKEN);
        when(s2sToken.s2sToken())
            .thenReturn(SERVICE_TOKEN);
        AuditEvent latestAuditEvent =
            buildAuditEvent(EVENT_NAME, STATE_DORMANT, LOCAL_DATE_TIME);

        List<AuditEvent> auditEventList = List.of(
            buildAuditEvent(EVENT_NAME, STATE_BO_CASE_STOPPED, LOCAL_DATE_TIME.minusMinutes(3)),
            latestAuditEvent
        );

        when(auditEventsResponse.getAuditEvents()).thenReturn(auditEventList);

        Optional<AuditEvent> actualAuditEvent =
            auditEventService.getLatestAuditEventInStateList(
                CASE_ID,
                List.of(STATE_NAME),
                userToken,
                s2sToken
            );

        assertThat(actualAuditEvent).isEmpty();
    }

    @Test
    void shouldIgnoreDormantStateAndReturnNextLatestMatchingState() {
        when(userToken.getBearerToken())
            .thenReturn(USER_TOKEN);
        when(s2sToken.s2sToken())
            .thenReturn(SERVICE_TOKEN);
        AuditEvent dormantAuditEvent =
            buildAuditEvent(EVENT_NAME, STATE_DORMANT, LOCAL_DATE_TIME);

        AuditEvent expectedAuditEvent =
            buildAuditEvent(EVENT_NAME, STATE_BO_CASE_STOPPED_REISSUE, LOCAL_DATE_TIME.minusMinutes(1));

        List<AuditEvent> auditEventList = List.of(
            expectedAuditEvent,
            dormantAuditEvent
        );

        when(auditEventsResponse.getAuditEvents()).thenReturn(auditEventList);

        Optional<AuditEvent> actualAuditEvent =
            auditEventService.getLatestAuditEventInStateList(
                CASE_ID,
                List.of(STATE_BO_CASE_STOPPED_REISSUE),
                userToken,
                s2sToken
            );

        assertThat(actualAuditEvent).isPresent().contains(expectedAuditEvent);
    }

    @Test
    void shouldReturnEmptyOptionalWhenAuditEventsIsEmptyForExcludingState() {
        when(userToken.getBearerToken())
            .thenReturn(USER_TOKEN);
        when(s2sToken.s2sToken())
            .thenReturn(SERVICE_TOKEN);
        when(auditEventsResponse.getAuditEvents()).thenReturn(List.of());

        Optional<AuditEvent> actualAuditEvent =
            auditEventService.getLatestAuditEventInStateList(
                CASE_ID,
                List.of(STATE_NAME),
                userToken,
                s2sToken
            );

        assertThat(actualAuditEvent).isEmpty();
    }

    private AuditEvent buildAuditEvent(String eventId, String stateId, LocalDateTime createdDate) {
        return AuditEvent.builder()
            .id(eventId)
            .stateId(stateId)
            .userFirstName("Tom")
            .userLastName("Jones")
            .createdDate(createdDate)
            .build();
    }
}
