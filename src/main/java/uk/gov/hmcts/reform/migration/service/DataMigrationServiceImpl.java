package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.migration.service.dtspb4583.Dtspb4583DataService;

import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private final Dtspb4583DataService dtspb4583DataService;

    @Override
    public Predicate<CaseDetails> accepts() {
        return cd -> cd != null && dtspb4583DataService.get(cd.getId()).isPresent();
    }

    @Override
    public Map<String, Object> rollback(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

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
