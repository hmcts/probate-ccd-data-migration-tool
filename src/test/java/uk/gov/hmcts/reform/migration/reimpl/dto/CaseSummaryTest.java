package uk.gov.hmcts.reform.migration.reimpl.dto;


import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaseSummaryTest {
    @Test
    void nonPositiveReferenceThrows() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new CaseSummary(0L, CaseType.CAVEAT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new CaseSummary(-1L, CaseType.CAVEAT)));
    }

    @Test
    void sameReferenceAndDifferentTypeThrows() {
        final CaseSummary first = new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION);
        final CaseSummary second = new CaseSummary(1L, CaseType.CAVEAT);

        assertThrows(IllegalStateException.class, () -> first.compareTo(second));
    }

    @Test
    void sameReferenceAndSameTypeEqual() {
        final Long firstRef = 1L;
        final Long secondRef = 1L;
        final Integer expected = firstRef.compareTo(secondRef);

        final CaseSummary first = new CaseSummary(firstRef, CaseType.GRANT_OF_REPRESENTATION);
        final CaseSummary second = new CaseSummary(secondRef, CaseType.GRANT_OF_REPRESENTATION);

        final Integer actual = first.compareTo(second);

        assertThat(actual, Matchers.equalTo(expected));
    }

    @Test
    void differentReferenceAndSameTypeDiffer() {
        final Long firstRef = 1L;
        final Long secondRef = 2L;
        final Integer expected = firstRef.compareTo(secondRef);

        final CaseSummary first = new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION);
        final CaseSummary second = new CaseSummary(2L, CaseType.GRANT_OF_REPRESENTATION);

        final Integer actual = first.compareTo(second);

        assertThat(actual, Matchers.equalTo(expected));
    }

    @Test
    void differentReferenceAndDifferentTypeDiffer() {
        final Long firstRef = 1L;
        final Long secondRef = 2L;
        final Integer expected = firstRef.compareTo(secondRef);

        final CaseSummary first = new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION);
        final CaseSummary second = new CaseSummary(2L, CaseType.CAVEAT);

        final Integer actual = first.compareTo(second);

        assertThat(actual, Matchers.equalTo(expected));
    }
}
