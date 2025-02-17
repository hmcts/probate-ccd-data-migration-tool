package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.TTL;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private static final String TTL_FIELD_NAME = "TTL";
    private static final String NO = "No";
    private static final int FOURTEEN_DAYS = 14;

    @Override
    public Predicate<CaseDetails> accepts() {
        return caseDetails -> caseDetails != null;
    }

    @Override
    public Map<String, Object> rollback(Map<String, Object> data) {
        if (data == null) {
            return null;
        } else {
            data.put(TTL_FIELD_NAME, new HashMap<>());
        }

        return data;
    }

    @Override
    public Map<String, Object> migrate(CaseDetails caseDetails) {
        if (caseDetails == null) {
            return null;
        }
        Map<String, Object> data = caseDetails.getData();
        if (data == null) {
            return null;
        }
        LocalDate lastModifiedDate = caseDetails.getLastModified().toLocalDate();
        TTL ttl = TTL.builder()
            .systemTTL(lastModifiedDate.plusDays(FOURTEEN_DAYS))
            .suspended(NO)
            .build();
        data.put(TTL_FIELD_NAME, ttl);
        return data;
    }
}
