package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class Dtspb5112ConfigTest {

    @Test
    void shouldCreateConfigSuccessfully() {
        Dtspb5112Config config = new Dtspb5112Config(
            "2025-01-01",
            "Probate"
        );

        assertThat(config.getRollbackDate())
            .isEqualTo(LocalDate.of(2025, 1, 1));

        assertThat(config.getPaymentServiceName())
            .isEqualTo("Probate");
    }
}
