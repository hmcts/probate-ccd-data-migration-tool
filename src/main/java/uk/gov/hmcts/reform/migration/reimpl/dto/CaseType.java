package uk.gov.hmcts.reform.migration.reimpl.dto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum CaseType {
    GRANT_OF_REPRESENTATION("GrantOfRepresentation"),
    CAVEAT("Caveat"),
    ;

    private final String ccdValue;

    CaseType(
            final String ccdValue) {
        this.ccdValue = ccdValue;
    }

    public String getCcdValue() {
        return this.ccdValue;
    }
}
