package uk.gov.hmcts.reform.migration.reimpl.dto;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
public record CaseSummary(Long reference, CaseType type) implements Comparable<CaseSummary> {

    public CaseSummary {
        Objects.requireNonNull(reference);
        Objects.requireNonNull(type);

        if (reference <= 0) {
            throw new IllegalArgumentException("Case reference cannot be negative");
        }
    }

    @Override
    public int compareTo(CaseSummary o) {
        final int compareReference = reference.compareTo(o.reference);
        if (compareReference != 0) {
            return compareReference;
        }
        if (type != o.type) {
            log.error("Case reference {} has been provided with two distinct case types ({}, {})",
                    reference,
                    type,
                    o.type);
            throw new IllegalStateException("A single case reference should never have distinct case types");
        }
        return 0;
    }
}
