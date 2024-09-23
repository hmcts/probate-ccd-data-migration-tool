package uk.gov.hmcts.reform.migration.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import uk.gov.hmcts.reform.domain.common.OrganisationEntityResponse;

@FeignClient(
    name = "rd-professional-api",
    url = "${prd.organisations.url}",
    configuration = FeignClientProperties.FeignClientConfiguration.class
)
public interface OrganisationApi {
    @GetMapping("/refdata/internal/v1/organisations/orgDetails/{userId}")
    OrganisationEntityResponse findOrganisationOfSolicitor(
            @RequestHeader("Authorization") String authorisation,
            @RequestHeader("ServiceAuthorization") String serviceAuthorization,
            @RequestParam(value = "userId") String userId
    );
}
