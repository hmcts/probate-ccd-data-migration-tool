package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.domain.common.AuditEvent;
import uk.gov.hmcts.reform.domain.common.AuditEventsResponse;
import uk.gov.hmcts.reform.migration.client.CaseDataApiV2;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditEventService {
    private final CaseDataApiV2 caseDataApi;

    public Optional<AuditEvent> getLatestAuditEventByName(String caseId, List<String> eventName,
                                                          String userToken, String authToken) {
        AuditEventsResponse auditEventsResponse
            = caseDataApi.getAuditEvents(userToken, authToken, false, caseId);

        return auditEventsResponse.getAuditEvents().stream()
            .filter(auditEvent -> !eventName.contains(auditEvent.getId()))
            .max(Comparator.comparing(AuditEvent::getCreatedDate));
    }

    public Optional<AuditEvent> getLatestAuditEventInStateList(String caseId, List<String> stateNames,
                                                               UserToken userToken, S2sToken authToken) {
        log.info("Getting latest audit event for caseId: {}", caseId);
        AuditEventsResponse auditEventsResponse
            = caseDataApi.getAuditEvents(userToken.getBearerToken(), authToken.s2sToken(), false, caseId);
        log.info("auditEventsResponse AuditEvents().size(): {}", auditEventsResponse.getAuditEvents().size());
        return auditEventsResponse.getAuditEvents().stream()
            .sorted(Comparator.comparing(AuditEvent::getCreatedDate).reversed())
            .filter(auditEvent -> stateNames.contains(auditEvent.getStateId()))
            .findFirst();
    }
}
