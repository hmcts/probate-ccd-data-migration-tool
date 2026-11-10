package uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob;

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
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class RemoveDeceasedDobElasticQueriesTest {
    RemoveDeceasedDobElasticQueries removeDeceasedDobElasticQueries;

    @Spy
    ElasticSearchQueryUtils elasticSearchQueryUtils;

    AutoCloseable closeableMocks;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        this.removeDeceasedDobElasticQueries = new RemoveDeceasedDobElasticQueries(elasticSearchQueryUtils);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeableMocks.close();
    }

    @Test
    void baseMigrationQueryHasEmptyReferences() {
        final Set<CaseSummary> caseRefs = Set.of();
        final Integer size = 1;
        final Optional<Long> fromReference = Optional.empty();

        final JSONObject actual = removeDeceasedDobElasticQueries.getGorMigrationQuery(
                caseRefs,
                size,
                fromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertFalse(actual.has("search_after")),
                () -> assertThat(actual, elasticsearchForCases(emptyIterable())));
    }

    @Test
    void baseMigrationQueryUsesFromReference() {
        final Set<CaseSummary> caseRefs = Set.of();
        final Integer size = 1;
        final Long fromReference = 1L;
        final Optional<Long> optFromReference = Optional.of(fromReference);

        final JSONObject actual = removeDeceasedDobElasticQueries.getGorMigrationQuery(
                caseRefs,
                size,
                optFromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertThat(actual, elasticsearchForCases(emptyIterable())),
                () -> assertThat(actual, elasticsearchHasSearchAfter(fromReference)));
    }

    @Test
    void baseMigrationQueryUsesSize() {
        final Set<CaseSummary> caseRefs = Set.of();
        final Integer size = 2;
        final Optional<Long> optFromReference = Optional.empty();

        final JSONObject actual = removeDeceasedDobElasticQueries.getGorMigrationQuery(
                caseRefs,
                size,
                optFromReference);

        assertAll(
                () -> assertThat(actual.get("size"), equalTo(size)),
                () -> assertFalse(actual.has("search_after")),
                () -> assertThat(actual, elasticsearchForCases(emptyIterable())));
    }

    @Test
    void specificMigrationQueryHasMatchingReferences() {
        final Set<Long> caseRefs = Set.of(1L, 2L, 3L);
        final Set<CaseSummary> caseSummaries = caseRefs.stream()
                .map(l -> new CaseSummary(l, CaseType.GRANT_OF_REPRESENTATION))
                .collect(Collectors.toSet());
        final Integer size = 1;
        final Optional<Long> fromReference = Optional.empty();

        final JSONObject actual = removeDeceasedDobElasticQueries.getGorMigrationQuery(
            caseSummaries,
            size,
            fromReference);

        assertAll(
            () -> assertThat(actual.get("size"), equalTo(size)),
            () -> assertFalse(actual.has("search_after")),
            () -> assertThat(actual, elasticsearchForCases(containsInAnyOrder(caseRefs.stream().toArray()))));
    }

    private static Matcher<Object> elasticsearchForCases(Matcher<Iterable<?>> contentsMatcher) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(final Object o) {
                if (!(o instanceof JSONObject esQuery)) {
                    return false;
                }

                final JSONObject query = esQuery.getJSONObject("query");
                final JSONObject bool = query.getJSONObject("bool");
                final JSONObject filter = bool.getJSONObject("filter");
                final JSONObject terms = filter.getJSONObject("terms");
                final JSONArray references = terms.getJSONArray("reference.keyword");

                final List<Object> contents = references.toList();

                return contentsMatcher.matches(contents);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("elasticsearch query with case references matching: [")
                        .appendText(contentsMatcher.toString())
                        .appendText("]");
            }
        };
    }

    private static Matcher<Object> elasticsearchHasSearchAfter(final Long fromReference) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(final Object o) {
                if (!(o instanceof JSONObject esQuery)) {
                    return false;
                }

                final JSONArray searchAfter = esQuery.getJSONArray("search_after");
                final List<Object> contents = searchAfter.toList();

                if (contents.size() != 1) {
                    return false;
                }

                return contents.getFirst().equals(fromReference);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("elasticsearch has key 'search_after' with single value: [")
                        .appendText(fromReference.toString())
                        .appendText("]");
            }
        };
    }
}
