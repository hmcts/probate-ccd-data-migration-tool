package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@FeignClient(
    name = "dtspb5112-service-request-client",
    url = "${payment.serviceRequest.url}"
)
public interface Dtspb5112ServiceRequestClient {
    String SERVICE_AUTHORIZATION = "ServiceAuthorization";

    @GetMapping("/payments")
    Dtspb5112PaymentsResponse retrievePayments(
        @RequestHeader(AUTHORIZATION) String authorisation,
        @RequestHeader(SERVICE_AUTHORIZATION) String serviceAuthorisation,
        @RequestParam(name = "service_name") String serviceName,
        @RequestParam(name = "ccd_case_number") String ccdCaseNumber
    );
}
