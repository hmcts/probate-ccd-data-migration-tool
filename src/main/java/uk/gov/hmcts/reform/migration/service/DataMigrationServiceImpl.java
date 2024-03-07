package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.AuditEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private final AuditEventService auditEventService;
    private List<String> createCaseFromBulkScanEvent = Arrays.asList("createCaseFromBulkScanEvent");

    @Override
    public Predicate<CaseDetails> accepts() {
        return caseDetails -> caseDetails == null ? false : true;
    }

    @Override
    public Map<String, Object> rollback(Long id, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        } else {
            data.put("channelChoice", null);
            log.info("channelChoice {}", data.get("channelChoice"));
        }
        return data;
    }

    @Override
    public Map<String, Object> migrate(Long caseId, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        }

        String channelChoice = "";
        if ("No".equals(data.get("paperForm"))) {
            channelChoice = "Digital";
        } else {
            AuditEvent auditEvent = getAuditEvent(caseId, userToken, authToken);
            log.info("Audit events {}", auditEvent);
            if (auditEvent != null) {
                channelChoice = "BulkScan";
            } else {
                channelChoice = "Paper";
            }
        }
        data.put("channelChoice", channelChoice);
        data.remove("paperForm");
        return data;
    }

    private AuditEvent getAuditEvent(Long caseId, String userToken, String authToken) {
        return auditEventService.getLatestAuditEventByName(caseId.toString(), createCaseFromBulkScanEvent,
            userToken, authToken).orElse(null);
    }
}
