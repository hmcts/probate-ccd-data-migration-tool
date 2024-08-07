package uk.gov.hmcts.reform.domain.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrganisationEntityResponse {

    @JsonProperty("organisationIdentifier")
    private String organisationIdentifier;
    @JsonProperty(value = "name")
    private String name;
    @JsonProperty
    private String statusMessage;
    @JsonProperty
    private String sraId;
    @JsonProperty
    private Boolean sraRegulated;
    @JsonProperty
    private String companyNumber;
    @JsonProperty
    private String companyUrl;
    @JsonProperty
    private List<String> paymentAccount;
    @Builder.Default
    @JsonProperty
    private List<String> pendingPaymentAccount = new ArrayList<String>();
    @JsonProperty
    @DateTimeFormat
    private LocalDateTime dateReceived;
    @JsonProperty
    @DateTimeFormat
    @JsonInclude(ALWAYS)
    @Builder.Default
    private LocalDateTime dateApproved = null;
}
