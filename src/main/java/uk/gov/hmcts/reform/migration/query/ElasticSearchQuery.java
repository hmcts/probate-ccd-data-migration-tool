package uk.gov.hmcts.reform.migration.query;

import lombok.Builder;

@Builder
public class ElasticSearchQuery {

    private static final String START_QUERY = """
        {
            "query": {
                "bool": {
                    "must_not": [
                        { "match": { "state": "Deleted" }},
                        { "match": { "state": "Draft" }},
                        { "match": { "state": "Pending" }},
                        { "match": { "state": "SolAdmonCreated" }},
                        { "match": { "state": "SolAppCreatedDeceasedDtls" }},
                        { "match": { "state": "SolAppUpdated" }},
                        { "match": { "state": "CaseCreated" }},
                        { "match": { "state": "CasePaymentFailed" }},
                        { "match": { "state": "SolProbateCreated" }},
                        { "match": { "state": "SolIntestacyCreated" }},
                        { "match": { "state": "applyforGrantPaperApplication" }},
                        { "match": { "state": "PAAppCreated" }},
                        { "exists": { "field": "data.applicationSubmittedDate" }}
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
