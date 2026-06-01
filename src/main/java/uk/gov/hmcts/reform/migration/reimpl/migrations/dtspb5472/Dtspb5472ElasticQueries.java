package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.time.LocalDate;
import java.util.Optional;

@Component
@Slf4j
public class Dtspb5472ElasticQueries {

    private final ElasticSearchQueryUtils elasticSearchQueryUtils;

    private static final String BASE_GOR_MIGRATION_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": [
                        {
                            "bool": {
                                "should": [
                                    {
                                        "terms": {
                                            "data.primaryApplicantRelationshipToDeceased.keyword": [
                                                "adoptedChild"
                                            ]
                                        }
                                    },
                                    {
                                        "terms": {
                                            "data.solsApplicantRelationshipToDeceased.keyword": [
                                                "ChildAdopted"
                                            ]
                                        }
                                    }
                                ],
                                "minimum_should_match": 1
                            }
                        }
                    ],
                    "must_not": [
                        {
                            "exists": {
                                "field": "data.primaryApplicantAdoptedIn"
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

    private static final String BASE_GOR_ROLLBACK_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": [
                        {
                            "exists": {
                                "field": "data.primaryApplicantAdoptedIn"
                            }
                        },
                        {
                            "bool": {
                                "should": [
                                    {
                                        "terms": {
                                            "data.primaryApplicantRelationshipToDeceased.keyword": [
                                                "child"
                                            ]
                                        }
                                    },
                                    {
                                        "terms": {
                                            "data.solsApplicantRelationshipToDeceased.keyword": [
                                                "Child"
                                            ]
                                        }
                                    }
                                ],
                                "minimum_should_match": 1
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

    public Dtspb5472ElasticQueries(ElasticSearchQueryUtils elasticSearchQueryUtils) {
        this.elasticSearchQueryUtils = elasticSearchQueryUtils;
    }

    public JSONObject getGorMigrationQuery(
            final Integer size,
            final Optional<Long> fromReference) {
        JSONObject migrationQuery = new JSONObject(BASE_GOR_MIGRATION_QUERY);

        final JSONObject sizedQuery = elasticSearchQueryUtils.addSize(migrationQuery, size);
        final JSONObject searchAfterQuery = elasticSearchQueryUtils.addSearchAfter(sizedQuery, fromReference);

        log.debug("GoR migration query: {}", searchAfterQuery);
        return searchAfterQuery;
    }

    public JSONObject getGorRollbackQuery(
            final Integer size,
            final LocalDate migrationDate,
            final Optional<Long> fromReference) {
        JSONObject rollbackQuery = new JSONObject(BASE_GOR_ROLLBACK_QUERY);

        final JSONObject sizedQuery = elasticSearchQueryUtils.addSize(rollbackQuery, size);
        final JSONObject searchAfterQuery = elasticSearchQueryUtils.addSearchAfter(sizedQuery, fromReference);
        final JSONObject lastModifiedQuery = elasticSearchQueryUtils.addLastModifiedFilter(
            searchAfterQuery,
            migrationDate);

        log.debug("GoR rollback query: {}", lastModifiedQuery);
        return lastModifiedQuery;
    }
}
