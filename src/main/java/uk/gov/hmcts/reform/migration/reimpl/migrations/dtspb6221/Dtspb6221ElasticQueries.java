package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb6221;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchQueryUtils;

import java.util.Optional;

@Component
@Slf4j
public class Dtspb6221ElasticQueries {

    private final ElasticSearchQueryUtils elasticSearchQueryUtils;

    private static final String BASE_MIGRATION_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": [
                        {
                            "terms": {
                                "data.registryLocation": [
                                    "London",
                                    "Manchester",
                                    "Birmingham",
                                    "Bristol"
                                ]
                            }
                        }
                    ],
                    "must_not": [
                        {
                            "terms": {
                                "state": [
                                    "BOCaseMatchingReissue",
                                    "BOCaseStoppedReissue",
                                    "BOExaminingReissue",
                                    "BOGrantIssued",
                                    "BOGrantIssuedRegistrarEscalation",
                                    "BOPostGrantIssued"
                                ]
                            }
                        }
                    ]
                }
            },
            "_source": ["reference"],
            "sort": [
                {
                    "reference.keyword": "asc"
                }
            ]
        }
        """;

    public Dtspb6221ElasticQueries(
        final ElasticSearchQueryUtils elasticSearchQueryUtils
    ) {
        this.elasticSearchQueryUtils = elasticSearchQueryUtils;
    }

    public JSONObject getMigrationQuery(
        final Integer size,
        final Optional<Long> fromReference
    ) {

        return finish(
            new JSONObject(BASE_MIGRATION_QUERY),
            size,
            fromReference
        );
    }

    private JSONObject finish(
        final JSONObject query,
        final Integer size,
        final Optional<Long> fromReference
    ) {
        final JSONObject searchAfterQuery =
            elasticSearchQueryUtils.addSearchAfter(
                elasticSearchQueryUtils.addSize(query, size),
                fromReference
            );

        log.debug("Migration query: {}", searchAfterQuery);

        return searchAfterQuery;
    }

    public JSONObject getRollbackQuery(final Integer size, final Optional<Long> fromReference) {

        final JSONObject rollbackQuery = new JSONObject("""
            {
                "query": {
                    "bool": {
                        "filter": [
                            {
                                "term": {
                                    "data.registryLocation.keyword": "ctsc"
                                }
                            }
                        ]
                    }
                },
                "_source": [
                    "reference"
                ],
                "sort": [
                    {
                        "reference.keyword": "asc"
                    }
                ]
            }
            """
        );

        return elasticSearchQueryUtils.addSearchAfter(
            elasticSearchQueryUtils.addSize(
                rollbackQuery,
                size
            ),
            fromReference
        );
    }
}
