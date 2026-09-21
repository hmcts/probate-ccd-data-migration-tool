package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Dtspb5112ElasticQueriesTest {

    private final Dtspb5112ElasticQueries queries = new Dtspb5112ElasticQueries(new ElasticSearchQueryUtils());

    @Test
    void shouldFilterNewlyExpiredCandidatesByExpiryAndLastModified() {
        JSONObject query = queries.expiredLastModifiedSince(
            100,
            Dtspb5112Constants.LIVE_STATES,
            LocalDate.of(2026, 9, 15),
            LocalDate.of(2026, 9, 1),
            Optional.empty()
        );

        JSONArray filters = query.getJSONObject("query")
            .getJSONObject("bool")
            .getJSONArray("filter");

        assertThat(filters.toString()).contains("\"data.expiryDate\":{\"lt\":\"2026-09-15\"}");
        assertThat(filters.toString()).contains("\"last_modified\":{\"gte\":\"2026-09-01\"}");
        assertThat(query.getInt("size")).isEqualTo(100);
    }

    @Test
    void shouldFilterExistingExpiredCandidatesOnlyByLiveStateAndExpiry() {
        JSONObject query = queries.expired(
            100,
            Dtspb5112Constants.LIVE_STATES,
            LocalDate.of(2026, 9, 15),
            Optional.empty()
        );

        String queryString = query.toString();
        assertThat(queryString)
            .contains("data.expiryDate")
            .doesNotContain("last_modified");
    }

    @Test
    void shouldFilterRollbackCandidatesByLastModifiedDate() {
        ElasticSearchQueryUtils utils = mock();

        when(utils.addSize(any(JSONObject.class), eq(1000)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(utils.addSearchAfter(
            any(JSONObject.class),
            eq(Optional.empty())
        )).thenAnswer(invocation -> invocation.getArgument(0));

        JSONObject query = queries.stateOnlyModifiedSince(
            1000,
            Set.of(Dtspb5112Constants.CAVEAT_CLOSED),
            LocalDate.of(2026, 1, 1),
            Optional.empty()
        );

        JSONArray filters = query
            .getJSONObject("query")
            .getJSONObject("bool")
            .getJSONArray("filter");

        JSONObject lastModifiedRange = filters
            .getJSONObject(1)
            .getJSONObject("range")
            .getJSONObject("last_modified");

        assertThat(lastModifiedRange.getString("gte"))
            .isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    void shouldQueryCasesWithExpiryModifiedSinceRollbackDate() {
        JSONObject query = queries.withExpiryModifiedSince(
            1000,
            Dtspb5112Constants.LIVE_STATES,
            LocalDate.of(2026, 1, 1),
            Optional.empty()
        );

        JSONArray filters = query
            .getJSONObject("query")
            .getJSONObject("bool")
            .getJSONArray("filter");

        assertThat(filters.toString())
            .contains("\"exists\":{\"field\":\"data.expiryDate\"}");

        assertThat(filters.toString())
            .contains("\"last_modified\":{\"gte\":\"2026-01-01T00:00:00Z\"}");
    }
}
