package uk.gov.hmcts.reform.migration.service.dtspb4583;

import uk.gov.hmcts.reform.migration.model.Dtspb4583Dates;

import java.util.Map;

interface LoaderService {
    Map<Long, Dtspb4583Dates> load();
}
