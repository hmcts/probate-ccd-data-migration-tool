package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5539;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Dtspb5539ConfigTest {
    private static final List<String> VALID_CASE_TYPES = List.of(
        "GrantOfRepresentation",
        "Caveat",
        "WillLodgement",
        "StandingSearch"
    );

    @Test
    void shouldCreateConfigSuccessfully() {
        Dtspb5539Config config = new Dtspb5539Config(
            LocalDate.of(2025, 1, 1),
            VALID_CASE_TYPES,
            "ABA6"
        );

        assertThat(config.getRollbackDate())
            .isEqualTo(LocalDate.of(2025, 1, 1));

        assertThat(config.getHmctsId())
            .isEqualTo("ABA6");

        assertThat(config.getCaseTypes())
            .containsExactly(CaseType.values());
    }

    @Test
    void shouldThrowExceptionWhenRollbackDateIsNull() {
        assertThatThrownBy(() ->
            new Dtspb5539Config(
                null,
                VALID_CASE_TYPES,
                "ABA6"
            ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("dtspb5539.rollback_date must not be null");
    }

    @Test
    void shouldThrowExceptionWhenCaseTypesIsNull() {
        assertThatThrownBy(() ->
            new Dtspb5539Config(
                LocalDate.now(),
                null,
                "ABA6"
            ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("dtspb5539.case_types must not be null");
    }

    @Test
    void shouldThrowExceptionWhenCaseTypesIsEmpty() {
        assertThatThrownBy(() ->
            new Dtspb5539Config(
                LocalDate.now(),
                List.of(),
                "ABA6"
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("dtspb5539.case_types must not be empty");
    }

    @Test
    void shouldThrowExceptionWhenCaseTypeIsInvalid() {
        assertThatThrownBy(() ->
            new Dtspb5539Config(
                LocalDate.now(),
                List.of("InvalidCaseType"),
                "ABA6"
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid case type value");
    }

    @Test
    void shouldThrowExceptionWhenHmctsIdIsNull() {
        assertThatThrownBy(() ->
            new Dtspb5539Config(
                LocalDate.now(),
                VALID_CASE_TYPES,
                null
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("dtspb5539.supplementary-data.hmctsid must not be null or blank");
    }

    @Test
    void shouldThrowExceptionWhenHmctsIdIsBlank() {
        assertThatThrownBy(() ->
            new Dtspb5539Config(
                LocalDate.now(),
                VALID_CASE_TYPES,
                " "
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("dtspb5539.supplementary-data.hmctsid must not be null or blank");
    }

    @Test
    void shouldTrimCaseTypeValues() {
        Dtspb5539Config config = new Dtspb5539Config(
            LocalDate.now(),
            List.of(
                " GrantOfRepresentation ",
                " Caveat "
            ),
            "ABA6"
        );

        assertThat(config.getCaseTypes())
            .containsExactly(
                CaseType.GRANT_OF_REPRESENTATION,
                CaseType.CAVEAT
            );
    }

}
