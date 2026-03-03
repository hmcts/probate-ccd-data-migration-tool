package uk.gov.hmcts.reform.migration.reimpl.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaseTypeTest {
    @Test
    void testFromCcdValue_Caveat_Returns() {
        final CaseType actual = CaseType.fromCcdValue(CaseType.CAVEAT.getCcdValue());

        assertThat(actual, is(CaseType.CAVEAT));
    }

    @Test
    void testFromCcdValue_GrantOfRepresentation_Returns() {
        final CaseType actual = CaseType.fromCcdValue(CaseType.GRANT_OF_REPRESENTATION.getCcdValue());

        assertThat(actual, is(CaseType.GRANT_OF_REPRESENTATION));
    }

    @Test
    void testFromCcdValue_Nonsense_Throws() {
        final String nonsense = UUID.randomUUID().toString();
        assertThrows(
                IllegalArgumentException.class,
                () -> CaseType.fromCcdValue(nonsense));
    }

    @Test
    void testFromCcdValue_CaveatLowerCase_Throws() {
        final String lowerCaseCaveat = CaseType.CAVEAT.getCcdValue().toLowerCase();
        assertThrows(
                IllegalArgumentException.class,
                () -> CaseType.fromCcdValue(lowerCaseCaveat));
    }

    @Test
    void testFromCcdValue_GrantOfRepresentationLowerCase_Throws() {
        final String lowerCaseGor = CaseType.GRANT_OF_REPRESENTATION.getCcdValue().toLowerCase();
        assertThrows(
                IllegalArgumentException.class,
                () -> CaseType.fromCcdValue(lowerCaseGor));
    }
}
