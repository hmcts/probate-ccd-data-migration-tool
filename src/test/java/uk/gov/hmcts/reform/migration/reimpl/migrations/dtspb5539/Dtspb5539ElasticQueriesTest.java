package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5539;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.hmcts.reform.migration.reimpl.TestUtils.jsonArrayWith;

public class Dtspb5539ElasticQueriesTest {
    Dtspb5539ElasticQueries dtspb5539ElasticQueries;

    @Spy
    ElasticSearchQueryUtils elasticSearchQueryUtils;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        this.dtspb5539ElasticQueries = new Dtspb5539ElasticQueries(elasticSearchQueryUtils);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeableMocks.close();
    }

    @Test
    void getMigrationQueryTest() {
        final Integer size = 1;
        final Optional<Long> fromReference = Optional.empty();
        final JSONObject actual = dtspb5539ElasticQueries.getMigrationQuery(
            size,
            fromReference);

        assertAll(
            () -> assertThat(actual.get("size"), equalTo(size)),
            () -> assertThrows(JSONException.class, () -> actual.get("search_after")));
    }

    @Test
    void nextMigrationQueryTest() {
        final Integer size = 1;
        final Long nextCase = 1L;
        final Optional<Long> fromReference = Optional.of(nextCase);
        final JSONObject actual = dtspb5539ElasticQueries.getMigrationQuery(
            size,
            fromReference);

        assertAll(
            () -> assertThat(actual.get("size"), equalTo(size)),
            () -> assertThat(actual.get("search_after"), jsonArrayWith(nextCase)));
    }
}
