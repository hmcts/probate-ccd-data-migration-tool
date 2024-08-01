package uk.gov.hmcts.reform.migration.query;

import lombok.Builder;

@Builder
public class ElasticSearchRollbackQuery {

    private static final String START_QUERY = """
        {
            "query": {
                "bool": {
                    "must": [
                        {
                            "exists": {
                                "field": "data.lastModifiedDateForDormant"
                            }
                        }
                    ],
                    "filter": [
                        {
                            "range": {
                                "last_modified": {
                                    "gte": "%s",
                                    "lte": "%s"
                                }
                            }
                        },
                        {
                            "bool": {
                                "should": [
                                   { "match": { "state": "BOCaseMatchingExamining" }},
                                   { "match": { "state": "BOCaseMatchingIssueGrant" }},
                                   { "match": { "state": "BOCaseQA" }},
                                   { "match": { "state": "BOReadyToIssue" }},
                                   { "match": { "state": "BORegistrarEscalation" }},
                                   { "match": { "state": "BOCaseStopped" }},
                                   { "match": { "state": "CasePrinted" }},
                                   { "match": { "state": "CaseCreated" }},
                                   { "match": { "state": "BOSotGenerated" }},
                                   { "match": { "state": "Dormant" }},
                                   { "match": { "state": "BORedecNotificationSent" }},
                                   { "match": { "state": "BOCaseStoppedAwaitRedec" }},
                                   { "match": { "state": "BOCaseStoppedReissue" }},
                                   { "match": { "state": "BOCaseMatchingReissue" }},
                                   { "match": { "state": "BOExaminingReissue" }},
                                   { "match": { "state": "BOCaseImported" }},
                                   { "match": { "state": "BOCaveatPermenant" }},
                                   { "match": { "state": "BOCaseWorkerEscalation" }},
                                   { "match": { "state": "BOPostGrantIssued" }}
                                ]
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
        }""";

    private static final String END_QUERY = "\n    }";

    private static final String SEARCH_AFTER = "\"search_after\": [%s]";

    private String searchAfterValue;
    private int size;
    private boolean initialSearch;

    public String getQuery() {
        if (initialSearch) {
            return getInitialQuery();
        } else {
            return getSubsequentQuery();
        }
    }

    private String getInitialQuery() {
        return String.format(START_QUERY, size) + END_QUERY;
    }

    private String getSubsequentQuery() {
        return String.format(START_QUERY, size) + "," + String.format(SEARCH_AFTER, searchAfterValue) + END_QUERY;
    }
}
