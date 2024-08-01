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
    private List<String> excludedEventList = Arrays.asList("boHistoryCorrection", "boCorrection");
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
            return data;
        }
    }

    @Override
    public Map<String, Object> migrate(Long caseId, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        } else {
            AuditEvent auditEvent = getAuditEvent(caseId, userToken, authToken);
            log.info("Audit events {}", auditEvent);
            data.put("lastModifiedDateForDormant", auditEvent.getCreatedDate());
        }
        return data;
    }

    private AuditEvent getAuditEvent(Long caseId, String userToken, String authToken) {
        return auditEventService.getLatestAuditEventByName(caseId.toString(), excludedEventList,
            userToken, authToken)
            .orElseThrow(() -> new IllegalStateException(String
            .format("Could not find any event other than %s event in audit", excludedEventList)));
    }
}
