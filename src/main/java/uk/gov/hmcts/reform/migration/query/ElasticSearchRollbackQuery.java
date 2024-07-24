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
                                "field": "data.boHandoffReasonList"
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
                                    "gte": "%s",
                                    "lte": "%s"
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
    private String startDateTime;
    private String endDateTime;
    private boolean initialSearch;

    public String getQuery() {
        if (initialSearch) {
            return getInitialQuery();
        } else {
            return getSubsequentQuery();
        }
    }

    private String getInitialQuery() {
        return String.format(START_QUERY, startDateTime, endDateTime, size) + END_QUERY;
    }

    private String getSubsequentQuery() {
        return String.format(START_QUERY, startDateTime, endDateTime, size) + ","
            + String.format(SEARCH_AFTER, searchAfterValue) + END_QUERY;
    }
}
