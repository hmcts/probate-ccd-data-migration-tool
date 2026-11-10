package uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.util.Optional;
import java.util.Set;

@Component
@Slf4j
public class RemoveDeceasedDobElasticQueries {

    private final ElasticSearchQueryUtils elasticSearchQueryUtils;

    private static final String BASE_GOR_MIGRATION_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": {
                        "terms": {
                            "reference.keyword": []
                        }
                    }
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

    public RemoveDeceasedDobElasticQueries(ElasticSearchQueryUtils elasticSearchQueryUtils) {
        this.elasticSearchQueryUtils = elasticSearchQueryUtils;
    }

    public JSONObject getGorMigrationQuery(
            final Set<CaseSummary> caseRefs,
            final Integer size,
            final Optional<Long> fromReference) {
        final JSONObject migrationQuery = new JSONObject(BASE_GOR_MIGRATION_QUERY);

        final JSONObject query = migrationQuery.getJSONObject("query");
        final JSONObject bool = query.getJSONObject("bool");
        final JSONObject filter = bool.getJSONObject("filter");
        final JSONObject terms = filter.getJSONObject("terms");
        final JSONArray references = terms.getJSONArray("reference.keyword");

        for (CaseSummary caseSummary : caseRefs) {
            references.put(caseSummary.reference());
        }

        final JSONObject sizedQuery = elasticSearchQueryUtils.addSize(migrationQuery, size);
        final JSONObject searchAfterQuery = elasticSearchQueryUtils.addSearchAfter(sizedQuery, fromReference);

        log.debug("GoR migration query: {}", searchAfterQuery);
        return searchAfterQuery;
    }
}
