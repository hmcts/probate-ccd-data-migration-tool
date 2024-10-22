package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {

    @Override
    public Predicate<CaseDetails> accepts() {
        return caseDetails -> caseDetails == null ? false : true;
    }

    @Override
    public Map<String, Object> rollback(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        return data;
    }

    @Override
    public Map<String, Object> migrate(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        return data;
    }
}
