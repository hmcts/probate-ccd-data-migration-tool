package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5005;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Dtspb5005ElasticQueriesTest {
    Dtspb5005ElasticQueries dtspb5005ElasticQueries;

    @BeforeEach
    void setUp() {
        this.dtspb5005ElasticQueries = new Dtspb5005ElasticQueries();
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

    @Test
    void addSizeThrowsIfNullSize() {
        final JSONObject empty = new JSONObject();
        final Integer size = null;

        assertThrows(NullPointerException.class, () -> dtspb5005ElasticQueries.addSize(empty, size));
    }

    @Test
    void addSizeThrowsIfZeroSize() {
        final JSONObject empty = new JSONObject();
        final Integer size = 0;

        assertThrows(IllegalArgumentException.class, () -> dtspb5005ElasticQueries.addSize(empty, size));
    }

    @Test
    void addSizeThrowsIfNegativeSize() {
        final JSONObject empty = new JSONObject();
        final Integer size = -1;

        assertThrows(IllegalArgumentException.class, () -> dtspb5005ElasticQueries.addSize(empty, size));
    }

    @Test
    void addSizeAddsSize() {
        final JSONObject empty = new JSONObject();
        final Integer size = 1;

        final JSONObject result = dtspb5005ElasticQueries.addSize(empty, size);

        assertThat(result.get("size"), equalTo(size));
    }

    @Test
    void addSearchAfterThrowsIfNullFromReference() {
        final JSONObject empty = new JSONObject();
        final Optional<Long> fromReference = null;

        assertThrows(NullPointerException.class, () -> dtspb5005ElasticQueries.addSearchAfter(empty, fromReference));
    }

    @Test
    void addSearchAfterDoesNothingIfEmptyFromReference() {
        final JSONObject empty = new JSONObject();
        final Optional<Long> fromReference = Optional.empty();

        final JSONObject result = dtspb5005ElasticQueries.addSearchAfter(empty, fromReference);

        assertTrue(result.isEmpty());
    }

    @Test
    void addSearchAfterAddsSearchAfterArrayFromReference() {
        final JSONObject empty = new JSONObject();
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);

        final JSONObject actual = dtspb5005ElasticQueries.addSearchAfter(empty, fromReference);

        assertThat(actual.get("search_after"), jsonArrayWith(nextCase));
    }

    @Test
    void addLastModifiedFilterThrowsIfNullDate() {
        final JSONObject empty = new JSONObject();
        final LocalDate migrationDate = null;

        assertThrows(NullPointerException.class, () -> dtspb5005ElasticQueries.addLastModifiedFilter(
                empty,
                migrationDate));
    }

    @Test
    void addLastModifiedFilterAddsLastModifiedFilter() {

        final JSONArray filter = new JSONArray();
        final JSONObject bool = new JSONObject(Map.of("filter", filter));
        final JSONObject query = new JSONObject(Map.of("bool", bool));
        final JSONObject rollbackQuery = new JSONObject(Map.of("query", query));

        final LocalDate migrationDate = LocalDate.of(2026, 1, 16);

        assertThat(rollbackQuery, not(hasFilterForDate(migrationDate)));

        final JSONObject result = dtspb5005ElasticQueries.addLastModifiedFilter(rollbackQuery, migrationDate);

        assertThat(result, hasFilterForDate(migrationDate));
    }

    private static Matcher<Object> jsonArrayWith(final Long nextCase) {
        return new BaseMatcher<>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("JSON array containing only: " + nextCase + " (i.e. <[" + nextCase + "]>)");
            }

            @Override
            public boolean matches(Object o) {
                if (!(o instanceof JSONArray)) {
                    return false;
                }

                final JSONArray jsonArray = (JSONArray) o;
                if (jsonArray.length() != 1) {
                    return false;
                }

                final Object entry = jsonArray.get(0);
                if (!(entry instanceof Long)) {
                    return false;
                }

                final Long entryValue = (Long) entry;
                if (!entryValue.equals(nextCase)) {
                    return false;
                }
                return true;
            }
        };
    }

    private static Matcher<Object> hasFilterForDate(final LocalDate localDate) {
        return new BaseMatcher<>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("JSON object with an elasticsearch filter for last_modified after ")
                    .appendText(localDate.toString());
            }

            @Override
            public boolean matches(Object o) {
                if (!(o instanceof JSONObject)) {
                    return false;
                }
                final JSONObject jsonObject = (JSONObject) o;
                final JSONObject query = jsonObject.optJSONObject("query");
                if (query == null) {
                    return false;
                }
                final JSONObject bool = query.optJSONObject("bool");
                if (bool == null) {
                    return false;
                }
                final JSONArray filter = bool.optJSONArray("filter");
                if (filter == null) {
                    return false;
                }
                for (int i = 0; i < filter.length(); i++) {
                    final JSONObject filterEntry = filter.optJSONObject(i);
                    if (filterEntry == null) {
                        continue;
                    }

                    final JSONObject range = filterEntry.optJSONObject("range");
                    if (range == null) {
                        continue;
                    }

                    final JSONObject lastModified = range.optJSONObject("last_modified");
                    if (lastModified == null) {
                        continue;
                    }

                    final String gte = lastModified.optString("gte");
                    if (gte == null) {
                        continue;
                    }
                    return gte.equals(localDate.toString());
                }
                return false;
            }
        };
    }
}
