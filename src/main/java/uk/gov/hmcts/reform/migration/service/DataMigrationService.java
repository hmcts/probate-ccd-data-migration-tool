package uk.gov.hmcts.reform.migration.service;

import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;
import java.util.function.Predicate;

public interface DataMigrationService<T> {
    Predicate<CaseDetails> accepts();

    T migrate(Long id, Map<String, Object> data, String auth, String serviceAuth);

    T rollback(Long id, Map<String, Object> data, String auth, String serviceAuth);
}
