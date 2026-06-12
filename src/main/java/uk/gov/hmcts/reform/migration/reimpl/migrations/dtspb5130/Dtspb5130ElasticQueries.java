package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class Dtspb5130ElasticQueries {

    private static final String BASE_GOR_MIGRATION_QUERY = """
    {
        "query": {
            "bool": {
                "filter": [
                    {
                        "terms": {
                            "data.evidenceHandled": [
                                "No"
                            ]
                        }
                    },
                    {
                        "match": {
                            "state": "BOCaseClosed"
                        }
                    }
                ]
            }
        },
        "_source": ["reference"],
        "size": %s,
        "sort": [
            {
                "reference.keyword": "asc"
            }
        ]
    }
    """;


    private static final String BASE_GOR_ROLLBACK_QUERY = """
    {
        "query": {
            "bool": {
                "filter": [
                {
                    "terms": {
                        "data.evidenceHandled": ["Yes"]
                    }
                },
                {
                    "match": {
                        "state": "BOCaseClosed"
                    }
                },
                {
                    "range": {
                        "last_modified": {
                            "gte": "%s"
                        }
                    }
                }
                ]
            }
       },
       "_source": ["reference"],
        "size": %s,
        "sort": [
            {
                "reference.keyword": "asc"
            }
        ]
    }
    """;

    public JSONObject getGorMigrationQuery(final Integer size) {
        JSONObject migrationQuery = new JSONObject(BASE_GOR_MIGRATION_QUERY);

        final JSONObject sizedQuery = addSize(migrationQuery, size);
        log.debug("GoR migration query: {}", sizedQuery);
        return sizedQuery;
    }

    public JSONObject getGorRollbackQuery(final LocalDate migrationDate, final Integer size) {
        JSONObject rollbackQuery = new JSONObject(BASE_GOR_ROLLBACK_QUERY);

        final JSONObject lastModifiedQuery = addLastModifiedFilter(rollbackQuery, migrationDate);
        final JSONObject sizedQuery = addSize(lastModifiedQuery, size);

        log.debug("GoR rollback query: {}", sizedQuery);
        return sizedQuery;
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

    JSONObject addLastModifiedFilter(
            final JSONObject rollbackQuery,
            final LocalDate migrationDate) {
        Objects.requireNonNull(migrationDate);

        final JSONObject lastModifiedFilter = new JSONObject();
        final JSONObject range = new JSONObject();
        final JSONObject lastModified = new JSONObject();
        final String migrationDateStr = migrationDate.toString();

        lastModifiedFilter.put("range", range);
        range.put("last_modified", lastModified);
        lastModified.put("gte", migrationDateStr);

        final JSONObject query = rollbackQuery.getJSONObject("query");
        final JSONObject bool = query.getJSONObject("bool");
        final JSONArray filters = bool.getJSONArray("filter");
        filters.put(lastModifiedFilter);

        return rollbackQuery;
    }
}
