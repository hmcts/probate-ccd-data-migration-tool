package uk.gov.hmcts.reform.migration.service.dtspb4583;

import uk.gov.hmcts.reform.migration.model.Dtspb4583Dates;

import java.util.Optional;

public interface Dtspb4583DataService {
    Optional<Dtspb4583Dates> get(Long id);

    String getReferences();
}
