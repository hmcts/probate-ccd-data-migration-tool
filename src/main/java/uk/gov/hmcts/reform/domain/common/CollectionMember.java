package uk.gov.hmcts.reform.domain.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Data
public class CollectionMember<T> {
    private final String id;
    private final T value;

    public CollectionMember(T value) {
        id = null;
        this.value = value;
    }
}
