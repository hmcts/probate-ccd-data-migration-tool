package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.json.JSONArray;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

class Dtspb5472ElasticQueriesTest {
    Dtspb5472ElasticQueries dtspb5472ElasticQueries;

    @Spy
    ElasticSearchQueryUtils elasticSearchQueryUtils;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        this.dtspb5472ElasticQueries = new Dtspb5472ElasticQueries(elasticSearchQueryUtils);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeableMocks.close();
    }

    @Test
    void baseGorMigration() {
        final Integer size = 1;
        final Optional<Long> fromReference = Optional.empty();
        final JSONObject actual = dtspb5472ElasticQueries.getGorMigrationQuery(
                size,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertFalse(actual.has("search_after")));
    }

    @Test
    void nextGorMigration() {
        final Integer size = 1;
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);
        final JSONObject actual = dtspb5472ElasticQueries.getGorMigrationQuery(
                size,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThat(actual.get("search_after"), jsonArrayWith(nextCase)));
    }

    @Test
    void baseGorRollback() {
        final Integer size = 1;
        final LocalDate migrationDate = LocalDate.of(2026, 1, 16);
        final Optional<Long> fromReference = Optional.empty();
        final JSONObject actual = dtspb5472ElasticQueries.getGorRollbackQuery(
                size,
                migrationDate,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertFalse(actual.has("search_after")),
                () -> assertThat(actual, hasFilterForDate(migrationDate)));
    }

    @Test
    void nextGorRollback() {
        final Integer size = 1;
        final LocalDate migrationDate = LocalDate.of(2026, 1, 16);
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);
        final JSONObject actual = dtspb5472ElasticQueries.getGorRollbackQuery(
                size,
                migrationDate,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThat(actual.get("search_after"), jsonArrayWith(nextCase)),
                () -> assertThat(actual, hasFilterForDate(migrationDate)));
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
