package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;

@Component
public class Dtspb5112ElasticQueries {
    private final ElasticSearchQueryUtils utils;

    public Dtspb5112ElasticQueries(ElasticSearchQueryUtils utils) {
        this.utils = utils;
    }

    public JSONObject stateOnly(Integer size, Collection<String> states, Optional<Long> fromReference) {
        return finish(base(states), size, fromReference);
    }

    public JSONObject missingExpiry(Integer size, Collection<String> states, Optional<Long> fromReference) {
        JSONObject query = base(states);
        query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not")
            .put(new JSONObject().put("exists", new JSONObject().put("field", "data.expiryDate")));
        return finish(query, size, fromReference);
    }

    public JSONObject withExpiry(Integer size, Collection<String> states, Optional<Long> fromReference) {
        JSONObject query = base(states);
        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter")
            .put(new JSONObject().put("exists", new JSONObject().put("field", "data.expiryDate")));
        return finish(query, size, fromReference);
    }

    public JSONObject expired(Integer size, Collection<String> states, LocalDate today, Optional<Long> fromReference) {
        JSONObject query = base(states);
        addExpiryBefore(query, today);
        return finish(query, size, fromReference);
    }

    public JSONObject expiredLastModifiedSince(
        Integer size,
        Collection<String> states,
        LocalDate today,
        LocalDate lastModifiedSince,
        Optional<Long> fromReference
    ) {
        JSONObject query = base(states);
        addExpiryBefore(query, today);
        JSONObject finished = finish(query, size, fromReference);
        return utils.addLastModifiedFilter(finished, lastModifiedSince);
    }

    private void addExpiryBefore(JSONObject query, LocalDate today) {
        query.getJSONObject("query")
            .getJSONObject("bool")
            .getJSONArray("filter")
            .put(new JSONObject().put(
                "range",
                new JSONObject().put("data.expiryDate", new JSONObject().put("lt", today))
            ));
    }

    private JSONObject base(Collection<String> states) {
        JSONArray stateValues = new JSONArray(states);
        JSONArray filter = new JSONArray()
            .put(new JSONObject().put("terms", new JSONObject().put("state.keyword", stateValues)));
        JSONObject bool = new JSONObject()
            .put("filter", filter)
            .put("must_not", new JSONArray());
        return new JSONObject()
            .put("query", new JSONObject().put("bool", bool))
            .put("_source", new JSONArray().put("reference"))
            .put("size", 0)
            .put("sort", new JSONArray().put(new JSONObject().put("reference.keyword", "asc")));
    }

    private JSONObject finish(JSONObject query, Integer size, Optional<Long> fromReference) {
        return utils.addSearchAfter(utils.addSize(query, size), fromReference);
    }

    public JSONObject stateOnlyModifiedSince(
        Integer size,
        Collection<String> states,
        LocalDate lastModifiedFrom,
        Optional<Long> fromReference
    ) {
        JSONObject query = base(states);

        query.getJSONObject("query")
            .getJSONObject("bool")
            .getJSONArray("filter")
            .put(
                new JSONObject().put(
                    "range",
                    new JSONObject().put(
                        "last_modified",
                        new JSONObject().put(
                            "gte",
                            lastModifiedFrom
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant()
                                .toString()
                        )
                    )
                )
            );

        return finish(query, size, fromReference);
    }

    public JSONObject withExpiryModifiedSince(
        Integer size,
        Collection<String> states,
        LocalDate lastModifiedFrom,
        Optional<Long> fromReference
    ) {
        JSONObject query = base(states);

        JSONArray filter = query.getJSONObject("query")
            .getJSONObject("bool")
            .getJSONArray("filter");

        filter.put(
            new JSONObject().put(
                "exists",
                new JSONObject().put("field", "data.expiryDate")
            )
        );

        filter.put(
            new JSONObject().put(
                "range",
                new JSONObject().put(
                    "last_modified",
                    new JSONObject().put(
                        "gte",
                        lastModifiedFrom
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant()
                            .toString()
                    )
                )
            )
        );

        return finish(query, size, fromReference);
    }
}
