package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(queryString).contains("data.expiryDate");
        assertThat(queryString).doesNotContain("last_modified");
    }
}
