package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586;


import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.hmcts.reform.migration.reimpl.TestUtils.hasFilterForDate;
import static uk.gov.hmcts.reform.migration.reimpl.TestUtils.jsonArrayWith;

class Dtspb5586ElasticQueriesTest {
    Dtspb5586ElasticQueries dtspb5586ElasticQueries;

    @Spy
    ElasticSearchQueryUtils elasticSearchQueryUtils;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        this.dtspb5586ElasticQueries = new Dtspb5586ElasticQueries(elasticSearchQueryUtils);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeableMocks.close();
    }

    @Test
    void baseGorMigration() {
        final Integer size = 1;
        final Optional<Long> fromReference = Optional.empty();
        final JSONObject actual = dtspb5586ElasticQueries.getGorMigrationQuery(size, fromReference);

        assertAll(
            () -> assertThat(actual.get("size"), equalTo(size)),
            () -> assertThat(actual.has("search_after"), equalTo(false))
        );
    }

    @Test
    void nextGorMigration() {
        final Integer size = 1;
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);
        final JSONObject actual = dtspb5586ElasticQueries.getGorMigrationQuery(
                size,
                fromReference);

        assertAll(
            () -> assertThat(actual.get("size"), equalTo(size)),
            () -> assertThat(actual.get("search_after"), jsonArrayWith(nextCase))
        );
    }

    @Test
    void baseGorRollback() {
        final Integer size = 1;
        final LocalDate migrationDate = LocalDate.of(2024, 07, 23);
        final Optional<Long> fromReference = Optional.empty();
        final JSONObject actual = dtspb5586ElasticQueries.getGorRollbackQuery(
                size,
                migrationDate,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThrows(JSONException.class, () -> actual.get("search_after")),
                () -> assertThat(actual, hasFilterForDate(migrationDate)));
    }

    @Test
    void nextGorRollback() {
        final Integer size = 1;
        final LocalDate migrationDate = LocalDate.of(2024, 07, 23);
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);
        final JSONObject actual = dtspb5586ElasticQueries.getGorRollbackQuery(
                size,
                migrationDate,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThat(actual.get("search_after"), jsonArrayWith(nextCase)),
                () -> assertThat(actual, hasFilterForDate(migrationDate)));
    }
}
