package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.AuditEvent;
import uk.gov.hmcts.reform.domain.common.Organisation;
import uk.gov.hmcts.reform.domain.common.OrganisationEntityResponse;
import uk.gov.hmcts.reform.domain.common.OrganisationPolicy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private final AuditEventService auditEventService;
    private final OrganisationApi organisationApi;
    private final CoreCaseDataApi coreCaseDataApi;
    private List<String> createCaseFromBulkScanEventEvent = Arrays.asList("createCaseFromBulkScanEvent");

    @Override
    public Predicate<CaseDetails> accepts() {
        return caseDetails -> caseDetails == null ? false : true;
    }

    @Override
    public Map<String, Object> rollback(Long id, Map<String, Object> data) {
        if (data == null) {
            return null;
        } else {
            return data;
        }
    }

    @Override
    public Map<String, Object> migrate(Long caseId, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        }

        System.out.println("paper Form field: " + data.get("paperForm"));

        String channelChoice = "";
        if("NO".equals(data.get("paperForm"))) { //determine the correct comparator
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
        return data;
    }

    private AuditEvent getAuditEvent(Long caseId, String userToken, String authToken) {
        return auditEventService.getLatestAuditEventByName(caseId.toString(), createCaseFromBulkScanEventEvent,
            userToken, authToken).orElse(null);
    }
}
