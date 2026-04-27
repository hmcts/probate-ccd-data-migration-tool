package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5005;


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

class Dtspb5005ElasticQueriesTest {
    Dtspb5005ElasticQueries dtspb5005ElasticQueries;

    @Spy
    ElasticSearchQueryUtils elasticSearchQueryUtils;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        this.dtspb5005ElasticQueries = new Dtspb5005ElasticQueries(elasticSearchQueryUtils);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeableMocks.close();
    }

    @Test
    void baseGorMigration() {
        final Integer size = 1;
        final Optional<Long> fromReference = Optional.empty();
        final JSONObject actual = dtspb5005ElasticQueries.getGorMigrationQuery(
                size,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThrows(JSONException.class, () -> actual.get("search_after")));
    }

    @Test
    void nextGorMigration() {
        final Integer size = 1;
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);
        final JSONObject actual = dtspb5005ElasticQueries.getGorMigrationQuery(
                size,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThat(actual.get("search_after"), jsonArrayWith(nextCase)));
    }

    @Test
    void baseCaveatMigration() {
        final Integer size = 1;
        final Optional<Long> fromReference = Optional.empty();
        final JSONObject actual = dtspb5005ElasticQueries.getCaveatMigrationQuery(
                size,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThrows(JSONException.class, () -> actual.get("search_after")));
    }

    @Test
    void nextCaveatMigration() {
        final Integer size = 1;
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);
        final JSONObject actual = dtspb5005ElasticQueries.getCaveatMigrationQuery(
                size,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThat(actual.get("search_after"), jsonArrayWith(nextCase)));
    }

    @Test
    void baseGorRollback() {
        final Integer size = 1;
        final LocalDate migrationDate = LocalDate.of(2026, 01, 16);
        final Optional<Long> fromReference = Optional.empty();
        final JSONObject actual = dtspb5005ElasticQueries.getGorRollbackQuery(
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
        final LocalDate migrationDate = LocalDate.of(2026, 01, 16);
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);
        final JSONObject actual = dtspb5005ElasticQueries.getGorRollbackQuery(
                size,
                migrationDate,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThat(actual.get("search_after"), jsonArrayWith(nextCase)),
                () -> assertThat(actual, hasFilterForDate(migrationDate)));
    }

    @Test
    void baseCaveatRollback() {
        final Integer size = 1;
        final LocalDate migrationDate = LocalDate.of(2026, 01, 16);
        final Optional<Long> fromReference = Optional.empty();
        final JSONObject actual = dtspb5005ElasticQueries.getCaveatRollbackQuery(
                size,
                migrationDate,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThrows(JSONException.class, () -> actual.get("search_after")),
                () -> assertThat(actual, hasFilterForDate(migrationDate)));
    }

    @Test
    void nextCaveatRollback() {
        final Integer size = 1;
        final LocalDate migrationDate = LocalDate.of(2026, 01, 16);
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);
        final JSONObject actual = dtspb5005ElasticQueries.getCaveatRollbackQuery(
                size,
                migrationDate,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThat(actual.get("search_after"), jsonArrayWith(nextCase)),
                () -> assertThat(actual, hasFilterForDate(migrationDate)));
    }
}
