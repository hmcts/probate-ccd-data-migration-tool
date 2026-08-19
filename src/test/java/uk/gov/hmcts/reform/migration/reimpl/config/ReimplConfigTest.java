package uk.gov.hmcts.reform.migration.reimpl.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresentAnd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReimplConfigTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void processCasesConfig_whenBlank_returnsEmptyOptional(final String input) {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesConfig(input);

        assertThat(actual, isEmpty());
    }

    @Test
    void processCasesConfig_whenCaseReferenceInvalid_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReimplConfig.processCasesConfig("invalid:Caveat"));
    }

    @Test
    void processCasesConfig_whenCaseTypeInvalid_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReimplConfig.processCasesConfig("1:invalid"));
    }

    @Test
    void processCasesConfig_NonpositiveCaseReference_ThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReimplConfig.processCasesConfig("0:Caveat"));
    }

    @Test
    void processCasesConfig_whenSingleCaveatProvided_returnsOneCase() {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesConfig("1:Caveat");

        assertAll(
                () -> assertThat(actual, isPresentAnd(hasSize(1))),
                () -> assertThat(actual, isPresentAnd(containsInAnyOrder(new CaseSummary(1L, CaseType.CAVEAT)))));
    }

    @Test
    void processCasesConfig_SingleGop_ReturnsOneCase() {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesConfig("1:GrantOfRepresentation");

        assertAll(
                () -> assertThat(actual, isPresentAnd(hasSize(1))),
                () -> assertThat(actual, isPresentAnd(containsInAnyOrder(
                        new CaseSummary(1L, CaseType.GRANT_OF_REPRESENTATION)))));
    }

    @Test
    void processCasesConfig_TwoCases_ReturnsTwoCase() {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesConfig(
                "1:Caveat,2:GrantOfRepresentation");

        assertAll(
                () -> assertThat(actual, isPresentAnd(hasSize(2))),
                () -> assertThat(actual, isPresentAnd(containsInAnyOrder(
                        new CaseSummary(1L, CaseType.CAVEAT),
                        new CaseSummary(2L, CaseType.GRANT_OF_REPRESENTATION)))));
    }

    @Test
    void processCasesConfig_TwoCasesWhitespace_ReturnsTwoCase() {
        final Optional<Set<CaseSummary>> actual = ReimplConfig.processCasesConfig(
                " 1 : Caveat , 2 : GrantOfRepresentation ");

        assertAll(
                () -> assertThat(actual, isPresentAnd(hasSize(2))),
                () -> assertThat(actual, isPresentAnd(containsInAnyOrder(
                        new CaseSummary(1L, CaseType.CAVEAT),
                        new CaseSummary(2L, CaseType.GRANT_OF_REPRESENTATION)))));
    }

    @Test
    void processCasesConfig_whenSeparatorMissing_throwsIllegalArgumentException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ReimplConfig.processCasesConfig("1Caveat")
        );
    }

    @Test
    void getMaximumResults_whenInitialRunDisabled_returnsEmpty() {
        final ReimplConfig config = createConfig(false, 10);

        final OptionalInt result = config.getMaximumResults();

        assertEquals(OptionalInt.empty(), result);
    }

    @Test
    void getMaximumResults_whenInitialRunEnabled_returnsInitialSize() {
        final ReimplConfig config = createConfig(true, 10);

        final OptionalInt result = config.getMaximumResults();

        assertEquals(OptionalInt.of(10), result);
    }

    private static ReimplConfig createConfig(
        final boolean initialRun,
        final int initialSize) {
        return new ReimplConfig(
            1,              // defaultThreadLimit
            "TEST",         // migrationId
            1,              // userTokenRefreshMarginMins
            1,              // s2sTokenRefreshMarginMins
            "",             // caseReferences/casesToMigrate
            "",             // casesToRestrictTo/casesToExclude
            10,             // querySize
            false,          // dryRun
            initialRun,
            initialSize
        );
    }
}
