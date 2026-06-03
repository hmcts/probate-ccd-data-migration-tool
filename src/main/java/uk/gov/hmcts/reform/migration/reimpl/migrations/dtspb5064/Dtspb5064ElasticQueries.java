package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5064;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class Dtspb5064ElasticQueries {

    private final ElasticSearchQueryUtils elasticSearchQueryUtils;


    private static final String BASE_CAVEAT_MIGRATION_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": [
                        {
                            "terms": {
                                "state.keyword": [
                                    "CaveatNotMatched"
                                ]
                            }
                        }
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

    private static final String BASE_CAVEAT_ROLLBACK_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": [
                        {
                            "terms": {
                                "state.keyword": [
                                    "CaveatMatching"
                                ]
                            }
                        }
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

    public Dtspb5064ElasticQueries(final ElasticSearchQueryUtils elasticSearchQueryUtils) {
        this.elasticSearchQueryUtils = Objects.requireNonNull(elasticSearchQueryUtils);
    }

    public JSONObject getCaveatMigrationQuery(
        final Integer size,
        final Optional<Long> fromReference) {
        JSONObject migrationQuery = new JSONObject(BASE_CAVEAT_MIGRATION_QUERY);

        final JSONObject sizedQuery = elasticSearchQueryUtils.addSize(migrationQuery, size);
        final JSONObject searchAfterQuery = elasticSearchQueryUtils.addSearchAfter(sizedQuery, fromReference);

        log.debug("Caveat migration query: {}", searchAfterQuery);
        return searchAfterQuery;
    }

    public JSONObject getCaveatRollbackQuery(
        final Integer size,
        final LocalDate migrationDate,
        final Optional<Long> fromReference) {
        JSONObject rollbackQuery = new JSONObject(BASE_CAVEAT_ROLLBACK_QUERY);

        final JSONObject sizedQuery = elasticSearchQueryUtils.addSize(rollbackQuery, size);
        final JSONObject searchAfterQuery = elasticSearchQueryUtils.addSearchAfter(sizedQuery, fromReference);
        final JSONObject lastModifiedQuery = elasticSearchQueryUtils.addLastModifiedFilter(
            searchAfterQuery,
            migrationDate);

        log.debug("Caveat rollback query: {}", lastModifiedQuery);
        return lastModifiedQuery;
    }
}
