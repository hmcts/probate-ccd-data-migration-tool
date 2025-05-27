package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {

    @Override
    public Predicate<CaseDetails> accepts() {
        return Objects::nonNull;
    }

    @Override
    public Map<String, Object> rollback(Map<String, Object> data) {
        return data;
    }

    @Override
    public Map<String, Object> migrate(final CaseDetails caseDetails) {
        if (caseDetails == null) {
            return null;
        }

        return caseDetails.getData();
    }
}
