package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.CollectionMember;
import uk.gov.hmcts.reform.domain.common.HandoffReason;

import java.util.ArrayList;
import java.util.List;
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
    public Map<String, Object> rollback(Long id, Map<String, Object> data) {
        if (data == null) {
            return null;
        } else {
            return data;
        }
    }

    @Override
    public Map<String, Object> migrate(Long caseId, Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        if ("Yes".equals(data.get("caseHandedOffToLegacySite"))) {
            List<CollectionMember<HandoffReason>> reason = new ArrayList<>();
            reason.add(new CollectionMember<>(null, HandoffReason.builder().handoffReason("Spreadsheet").build()));
            data.put("boHandoffReasonList", reason);
        } else {
            data.put("boHandoffReasonList", null);
        }

        return data;
    }
}
