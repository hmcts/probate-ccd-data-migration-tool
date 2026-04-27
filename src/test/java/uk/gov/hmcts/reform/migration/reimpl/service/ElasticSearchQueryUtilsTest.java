package uk.gov.hmcts.reform.migration.reimpl.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.hmcts.reform.migration.reimpl.TestUtils.hasFilterForDate;
import static uk.gov.hmcts.reform.migration.reimpl.TestUtils.jsonArrayWith;

class ElasticSearchQueryUtilsTest {

    ElasticSearchQueryUtils elasticSearchQueryUtils;

    @BeforeEach
    void setUp() {
        this.elasticSearchQueryUtils = new ElasticSearchQueryUtils();
    }

    @Test
    void addSizeThrowsIfNullSize() {
        final JSONObject empty = new JSONObject();
        final Integer size = null;

        assertThrows(NullPointerException.class, () -> elasticSearchQueryUtils.addSize(empty, size));
    }

    @Test
    void addSizeThrowsIfZeroSize() {
        final JSONObject empty = new JSONObject();
        final Integer size = 0;

        assertThrows(IllegalArgumentException.class, () -> elasticSearchQueryUtils.addSize(empty, size));
    }

    @Test
    void addSizeThrowsIfNegativeSize() {
        final JSONObject empty = new JSONObject();
        final Integer size = -1;

        assertThrows(IllegalArgumentException.class, () -> elasticSearchQueryUtils.addSize(empty, size));
    }

    @Test
    void addSizeAddsSize() {
        final JSONObject empty = new JSONObject();
        final Integer size = 1;

        final JSONObject result = elasticSearchQueryUtils.addSize(empty, size);

        assertThat(result.get("size"), equalTo(size));
    }

    @Test
    void addSearchAfterThrowsIfNullFromReference() {
        final JSONObject empty = new JSONObject();
        final Optional<Long> fromReference = null;

        assertThrows(NullPointerException.class, () -> elasticSearchQueryUtils.addSearchAfter(empty, fromReference));
    }

    @Test
    void addSearchAfterDoesNothingIfEmptyFromReference() {
        final JSONObject empty = new JSONObject();
        final Optional<Long> fromReference = Optional.empty();

        final JSONObject result = elasticSearchQueryUtils.addSearchAfter(empty, fromReference);

        assertTrue(result.isEmpty());
    }

    @Test
    void addSearchAfterAddsSearchAfterArrayFromReference() {
        final JSONObject empty = new JSONObject();
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);

        final JSONObject actual = elasticSearchQueryUtils.addSearchAfter(empty, fromReference);

        assertThat(actual.get("search_after"), jsonArrayWith(nextCase));
    }

    @Test
    void addLastModifiedFilterThrowsIfNullDate() {
        final JSONObject empty = new JSONObject();
        final LocalDate migrationDate = null;

        assertThrows(NullPointerException.class, () -> elasticSearchQueryUtils.addLastModifiedFilter(
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

        final JSONObject result = elasticSearchQueryUtils.addLastModifiedFilter(rollbackQuery, migrationDate);

        assertThat(result, hasFilterForDate(migrationDate));
    }
}
