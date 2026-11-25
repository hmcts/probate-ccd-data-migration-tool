package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb6221;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class Dtspb6221ConfigTest {

    @Test
    void shouldParseRollbackDate() {
        Dtspb6221Config config =
            new Dtspb6221Config("2026-11-20");

        assertThat(config.getRollbackDate())
            .isEqualTo(LocalDate.of(2026, 11, 20));
    }
}
