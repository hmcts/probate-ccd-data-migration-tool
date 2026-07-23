package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5539;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.util.Optional;

@Component
@Slf4j
public class Dtspb5539ElasticQueries {

    private final ElasticSearchQueryUtils elasticSearchQueryUtils;

    /* The case-migration.elasticsearch.querySize environment variable controls the batch size,
     * determining how many cases are included in each migration batch.
     */
    private static final String BASE_MIGRATION_QUERY = """
        {
          "query": {
            "bool": {
              "should": [
                {
                  "bool": {
                    "must_not": [
                      {
                        "exists": {
                          "field": "supplementary_data.HMCTSServiceId"
                        }
                      }
                    ]
                  }
                },
                {
                  "bool": {
                    "must_not": [
                      {
                        "exists": {
                          "field": "data.SearchCriteria"
                        }
                      }
                    ]
                  }
                }
              ],
              "minimum_should_match": 1
            }
          },
          "_source": ["reference"],
          "size": 100,
          "sort": [
            {
              "reference.keyword": "asc"
            }
          ]
        }
        """;

    public Dtspb5539ElasticQueries(ElasticSearchQueryUtils elasticSearchQueryUtils) {
        this.elasticSearchQueryUtils = elasticSearchQueryUtils;
    }

    public JSONObject getMigrationQuery(
        final Integer size,
        final Optional<Long> fromReference) {
        JSONObject migrationQuery = new JSONObject(BASE_MIGRATION_QUERY);

        final JSONObject sizedQuery = elasticSearchQueryUtils.addSize(migrationQuery, size);
        final JSONObject searchAfterQuery = elasticSearchQueryUtils.addSearchAfter(sizedQuery, fromReference);

        log.info("Migration query: {}", searchAfterQuery);
        return searchAfterQuery;
    }

}
