package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5539;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class Dtspb5539ElasticQueries {
    private static final String BASE_GOR_MIGRATION_QUERY = """
        {
            "query": {
                "match_all": {}
            },
            "_source": ["reference"],
            "size": 0,
            "sort": [
                {
                    "reference.keyword": "asc"
                }
            ]
        }
        """;

    public JSONObject getMigrationQuery(
        final Integer size,
        final Optional<Long> fromReference) {
        JSONObject migrationQuery = new JSONObject(BASE_GOR_MIGRATION_QUERY);

        final JSONObject sizedQuery = addSize(migrationQuery, size);
        final JSONObject searchAfterQuery = addSearchAfter(sizedQuery, fromReference);

        log.info("Migration query: {}", searchAfterQuery);
        return searchAfterQuery;
    }

    JSONObject addSize(
        final JSONObject query,
        final Integer size) {
        Objects.requireNonNull(size);

        if (size <= 0) {
            log.error("Requested query size must be greater than zero, found {}", size);
            throw new IllegalArgumentException("Requested query size must be greater than zero");
        }
        query.put("size", size);

        return query;
    }

    JSONObject addSearchAfter(
        final JSONObject query,
        final Optional<Long> fromReference) {
        Objects.requireNonNull(fromReference);

        if (fromReference.isPresent()) {
            final Long reference = fromReference.get();
            final JSONArray fromRef = new JSONArray();
            fromRef.put(reference);
            query.put("search_after", fromRef);
        }

        return query;
    }
}
