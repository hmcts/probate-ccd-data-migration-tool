package uk.gov.hmcts.reform.migration.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.AuditEventsResponse;

import java.util.Map;

@FeignClient(
    name = "core-case-data-api-v2",
    url = "${core_case_data.api.url}"
)
public interface CaseDataApiV2 {
    @GetMapping("/cases/{caseId}/events")
    AuditEventsResponse getAuditEvents(
        @RequestHeader("Authorization") String authorisation,
        @RequestHeader("ServiceAuthorization") String serviceAuthorization,
        @RequestHeader("experimental") boolean experimental,
        @PathVariable("caseId") String caseId
    );

    @RequestMapping(
        method = RequestMethod.POST,
        value = "/cases/{caseId}/supplementary-data"
    )
    CaseDetails submitSupplementaryData(
        @RequestHeader("Authorization") String authorisation,
        @RequestHeader("ServiceAuthorization") String serviceAuthorisation,
        @PathVariable("caseId") String caseId,
        @RequestBody Map<String, Map<String, Map<String, Object>>> supplementaryData
    );
}
