package uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob;

import org.junit.jupiter.api.Test;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.github.npathai.hamcrestopt.OptionalMatchers.isPresentAnd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RemoveDeceasedDobConfigTest {
    @Test
    void shouldCreateWithNullCasesToEmpty() {
        final RemoveDeceasedDobConfig config = new RemoveDeceasedDobConfig(null);

        assertThat(config.getCaseRefs(), isEmpty());
    }

    @Test
    void shouldCreateWithEmptyCasesToEmpty() {
        final RemoveDeceasedDobConfig config = new RemoveDeceasedDobConfig("");

        assertThat(config.getCaseRefs(), isEmpty());
    }

    @Test
    void shouldCreateWithBlankCasesToEmpty() {
        final RemoveDeceasedDobConfig config = new RemoveDeceasedDobConfig("   ");

        assertThat(config.getCaseRefs(), isEmpty());
    }

    @Test
    void shouldCreateWithSingleCaseToSingle() {
        final RemoveDeceasedDobConfig config = new RemoveDeceasedDobConfig("1");

        assertAll(
            () -> assertThat(config.getCaseRefs(), isPresentAnd(hasSize(1))));
    }

    @Test
    void shouldCreateWithRepeatCaseToSingle() {
        final RemoveDeceasedDobConfig config = new RemoveDeceasedDobConfig("1,1");

        assertAll(
            () -> assertThat(config.getCaseRefs(), isPresentAnd(hasSize(1))));
    }

    @Test
    void shouldFailToCreateWithOnlyNonnumericCase() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RemoveDeceasedDobConfig("hello"));
    }

    @Test
    void shouldFailToCreateWithMixedNonnumericCase() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new RemoveDeceasedDobConfig("1,2,hello"));
    }
}
