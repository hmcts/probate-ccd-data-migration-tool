package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dtspb5112PaymentsResponse {
    @JsonProperty("payments")
    private List<Dtspb5112PaymentDto> payments;
}
