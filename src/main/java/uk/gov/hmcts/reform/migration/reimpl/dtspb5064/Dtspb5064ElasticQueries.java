package uk.gov.hmcts.reform.migration.reimpl.dtspb5064;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class Dtspb5064ElasticQueries {

    private static final String baseMigrationQuery = """
        {
            "query": {
                "bool": {
                    "must": [
                        { "match": { "state": "CaveatNotMatched" }}
                    ]
                }
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

    private static final String baseRollbackQuery = """
        {
            "query": {
                "bool": {
                    "must": [
                        { "match": { "state": "AwaitingCaveatResolution" }}
                    ]
                }
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

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public JSONObject getMigrationQuery(
            final Integer size,
            final Optional<Long> fromReference) {
        Objects.requireNonNull(size);
        Objects.requireNonNull(fromReference);

        if (size <= 0) {
            log.error("Requested query size must be greater than zero, found {}", size);
            throw new IllegalArgumentException("Requested query size must be greater than zero");
        }

        JSONObject migrationQuery = new JSONObject(baseMigrationQuery);

        migrationQuery.put("size", size);

        if (fromReference.isPresent()) {
            migrationQuery.put("search_after", fromReference.get());
        }

        return migrationQuery;
    }

    public JSONObject getRollbackQuery(
            final Integer size,
            final Date migrationDate,
            final Optional<Long> fromReference) {
        Objects.requireNonNull(size);
        Objects.requireNonNull(migrationDate);
        Objects.requireNonNull(fromReference);

        if (size <= 0) {
            log.error("Requested query size must be greater than zero, found {}", size);
            throw new IllegalArgumentException("Requested query size must be greater than zero");
        }

        JSONObject rollbackQuery = new JSONObject(baseRollbackQuery);

        rollbackQuery.put("size", size);

        if (fromReference.isPresent()) {
            rollbackQuery.put("search_after", fromReference.get());
        }

        final JSONObject lastModifiedFilter = lastModifiedFilter(migrationDate);

        final JSONObject query = rollbackQuery.getJSONObject("query");
        final JSONObject bool = query.getJSONObject("bool");
        final JSONArray filters = bool.getJSONArray("filter");
        filters.put(lastModifiedFilter);

        return rollbackQuery;
    }

    JSONObject lastModifiedFilter(final Date migrationDate) {
        final JSONObject lastModifiedFilter = new JSONObject();
        final JSONObject range = new JSONObject();
        final JSONObject lastModified = new JSONObject();
        final String migrationDateStr = dateFormat.format(migrationDate);

        lastModifiedFilter.put("range", range);
        range.put("last_modified", lastModified);
        lastModified.put("gte", migrationDateStr);

        return lastModifiedFilter;
    }
}
