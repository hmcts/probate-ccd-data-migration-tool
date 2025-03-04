package uk.gov.hmcts.reform.domain.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TTL {
    @JsonProperty("SystemTTL")
    private LocalDate systemTTL;
    @JsonProperty("OverrideTTL")
    private LocalDate overrideTTL;
    @JsonProperty("Suspended")
    private String suspended;
}
