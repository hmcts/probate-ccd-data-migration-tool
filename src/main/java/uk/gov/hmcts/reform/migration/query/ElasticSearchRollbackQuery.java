package uk.gov.hmcts.reform.migration.query;

import lombok.Builder;

@Builder
public class ElasticSearchRollbackQuery {

    private static final String START_QUERY = """
        {
          "query": {
            "bool": {
              "must": [
                   { "exists": { "field": "data.applicationSubmittedDate" }}
              ],
              "filter":
                   [
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
                                   { "match": { "state": "CasePrinted" }},
                                   { "match": { "state": "BOCaseStoppedAwaitRedec" }},
                                   { "match": { "state": "CaseCreated" }},
                                   { "match": { "state": "BOCaseImported" }},
                                   { "match": { "state": "BOCaseMatchingIssueGrant" }},
                                   { "match": { "state": "BOCaseMatchingReissue" }},
                                   { "match": { "state": "BOCaseQA" }},
                                   { "match": { "state": "BOCaseStopped" }},
                                   { "match": { "state": "BOCaseStoppedReissue" }},
                                   { "match": { "state": "BOCaveatPermenant" }},
                                   { "match": { "state": "Dormant" }},
                                   { "match": { "state": "BOGrantIssued" }},
                                   { "match": { "state": "BOSotGenerated" }},
                                   { "match": { "state": "PAAppCreated" }},
                                   { "match": { "state": "BOPostGrantIssued" }},
                                   { "match": { "state": "BOReadyToIssue" }},
                                   { "match": { "state": "BORedecNotificationSent" }},
                                   { "match": { "state": "BORegistrarEscalation" }},
                                   { "match": { "state": "BOCaseWorkerEscalation" }},
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
