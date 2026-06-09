package uk.gov.hmcts.reform.migration.reimpl.service;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class ElasticSearchQueryUtils {

    public ElasticSearchQueryUtils() {
    }

    public JSONObject addSize(
            final JSONObject query,
            final Integer size) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(size, "size must not be null");

        if (size <= 0) {
            log.error("Requested query size must be greater than zero, found {}", size);
            throw new IllegalArgumentException("Requested query size must be greater than zero");
        }

        query.put("size", size);
        return query;
    }

    public JSONObject addSearchAfter(
            final JSONObject query,
            final Optional<Long> fromReference) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(fromReference, "fromReference must not be null");

        fromReference.ifPresent(reference -> {
            JSONArray searchAfter = new JSONArray();
            searchAfter.put(reference);
            query.put("search_after", searchAfter);
        });

        return query;
    }

    public JSONObject addLastModifiedFilter(
            final JSONObject rollbackQuery,
            final LocalDate migrationDate) {
        Objects.requireNonNull(rollbackQuery, "rollbackQuery must not be null");
        Objects.requireNonNull(migrationDate, "migrationDate must not be null");

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
