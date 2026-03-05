package uk.gov.hmcts.reform.migration.reimpl.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;

import java.util.Optional;
import java.util.Set;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresentAnd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReimplConfigTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void testCasesToFilterTo_BlankString_GivesEmptyOptional(final String input) {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesToFilterTo(input);

        assertThat(actual, isEmpty());
    }

    @Test
    void testCasesToFilterTo_InvalidCaseReference_ThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReimplConfig.processCasesToFilterTo("invalid:Caveat"));
    }

    @Test
    void testCasesToFilterTo_InvalidCaseType_ThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReimplConfig.processCasesToFilterTo("1:invalid"));
    }

    @Test
    void testCasesToFilterTo_NonpositiveCaseReference_ThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReimplConfig.processCasesToFilterTo("0:Caveat"));
    }

    @Test
    void testCasesToFilterTo_SingleCaveat_ReturnsOneCase() {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesToFilterTo("1:Caveat");

        assertAll(
                () -> assertThat(actual, isPresentAnd(hasSize(1))),
                () -> assertThat(actual, isPresentAnd(containsInAnyOrder(new CaseSummary(1L, CaseType.CAVEAT)))));
    }

    @Test
    void testCasesToFilterTo_SingleGop_ReturnsOneCase() {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesToFilterTo("1:GrantOfRepresentation");

        assertAll(
                () -> assertThat(actual, isPresentAnd(hasSize(1))),
                () -> assertThat(actual, isPresentAnd(containsInAnyOrder(
                        new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION)))));
    }

    @Test
    void testCasesToFilterTo_TwoCases_ReturnsTwoCase() {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesToFilterTo(
                "1:Caveat,2:GrantOfRepresentation");

        assertAll(
                () -> assertThat(actual, isPresentAnd(hasSize(2))),
                () -> assertThat(actual, isPresentAnd(containsInAnyOrder(
                        new CaseSummary(1L, CaseType.CAVEAT),
                        new CaseSummary(2L, CaseType.GRANT_OF_REPRESENTATION)))));
    }

    @Test
    void testCasesToFilterTo_TwoCasesWhitespace_ReturnsTwoCase() {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesToFilterTo(
                " 1 : Caveat , 2 : GrantOfRepresentation ");

        assertAll(
                () -> assertThat(actual, isPresentAnd(hasSize(2))),
                () -> assertThat(actual, isPresentAnd(containsInAnyOrder(
                        new CaseSummary(1L, CaseType.CAVEAT),
                        new CaseSummary(2L, CaseType.GRANT_OF_REPRESENTATION)))));
    }
}
