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
    private List<String> createCaseFromBulkScan = Arrays.asList("createCaseFromBulkScan");

    @Override
    public Predicate<CaseDetails> accepts() {
        return caseDetails -> caseDetails == null ? false : true;
    }

    @Override
    public Map<String, Object> rollback(Long id, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        } else {
            data.put("applicationSubmittedDate", null);
            log.info("applicationSubmittedDate {}", data.get("applicationSubmittedDate"));
        }
        return data;
    }

    @Override
    public Map<String, Object> migrate(Long caseId, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        }

        if (null == data.get("applicationSubmittedDate")) {
            Object applicationCreationDate = data.get("applicationCreationDate");
            data.put("applicationSubmittedDate", applicationCreationDate);
        }

        return data;
    }
}
