package uk.gov.hmcts.reform.migration.query;

import lombok.Builder;

@Builder
public class ElasticSearchQuery {

    private static final String START_QUERY = """
        {
            "query": {
                "bool": {
                    "must": [
                        {
                            "exists": {
                                "field": "data.caseHandedOffToLegacySite"
                            }
                        },
                        {
                            "term": {
                                "data.caseHandedOffToLegacySite": "Yes"
                            }
                        }
                    ],
                    "should": [
                        {"match": { "state": "CaseCreated" }},
                        {"match": { "state": "CasePaymentFailed" }},
                        {"match": { "state": "Stopped" }},
                        {"match": { "state": "Dormant" }},
                        {"match": { "state": "CasePrinted" }},
                        {"match": { "state": "BOCaseStopped" }},
                        {"match": { "state": "BOCaveatPermenant" }},
                        {"match": { "state": "BORegistrarEscalation" }},
                        {"match": { "state": "BOReadyToIssue" }},
                        {"match": { "state": "BOCaseQA" }},
                        {"match": { "state": "BOCaseMatchingIssueGrant" }},
                        {"match": { "state": "BOCaseClosed" }},
                        {"match": { "state": "BOCaseImported" }},
                        {"match": { "state": "BOExaminingReissue" }},
                        {"match": { "state": "BOCaseMatchingReissue" }},
                        {"match": { "state": "BOCaseStoppedReissue" }},
                        {"match": { "state": "BOCaseStoppedAwaitRedec" }},
                        {"match": { "state": "BORedecNotificationSent" }},
                        {"match": { "state": "BOSotGenerated" }},
                        {"match": { "state": "BOCaseWorkerEscalation" }}
                    ],
                    "filter": [
                        {
                            "range": {
                                "last_modified": {
                                    "gte": "2022-08-18T00:00:00"
                                }
                            }
                        }
                    ]
                }
            },
            "_source": ["reference", "data.caseHandedOffToLegacySite"],
            "size": %s,
            "sort": [
                {
                    "reference.keyword": "asc"
                }
            ]""";

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
