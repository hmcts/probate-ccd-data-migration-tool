package uk.gov.hmcts.reform.domain.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ClientException extends RuntimeException {

    private final int statusCode;
    private final String message;
}
