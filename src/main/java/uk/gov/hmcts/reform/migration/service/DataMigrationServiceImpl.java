package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.AuditEvent;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private final AuditEventService auditEventService;
    private final List<String> creationEventList = Arrays.asList(
        "createCaseFromBulkScan",
        "createCase",
        "createCaseWithoutPayment",
        "boImportGrant",
        "paymentSuccessApp",
        "paymentSuccessCase",
        "applyforGrantPaperApplication",
        "applyForGrant");
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    protected static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT);

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
        }
        return data;
    }

    @Override
    public Map<String, Object> migrate(Long caseId, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        } else if (null == data.get("applicationSubmittedDate")) {
            AuditEvent auditEvent = getAuditEvent(caseId, userToken, authToken);
            log.info("Audit events {}", auditEvent);

            String createdDate = dateTimeFormatter.format(auditEvent.getCreatedDate());
            data.put("applicationSubmittedDate", createdDate);
        }
        return data;
    }

    private AuditEvent getAuditEvent(Long caseId, String userToken, String authToken) {
        return auditEventService.getCaseCreationAuditEventByName(caseId.toString(), creationEventList,
            userToken, authToken)
            .orElseThrow(() -> new IllegalStateException(String
            .format("Could not find any event other than %s event in audit", creationEventList)));
    }
}
