package uk.gov.hmcts.reform.migration.query;

import lombok.Builder;

@Builder
public class ElasticSearchQuery {

    private static final String START_QUERY = """
        {
            "query": {
                "bool": {
                  "should": [
                    {
                      "bool": {
                        "filter": [
                          { "term": { "case_type_id.keyword": "GrantOfRepresentation" } },
                          { "term": { "state.keyword": "Deleted" } }
                        ]
                      }
                    },
                    {
                      "bool": {
                        "filter": [
                          { "term": { "case_type_id.keyword": "GrantOfRepresentation" } },
                          {
                            "terms": {
                              "state.keyword": [
                                "Pending",
                                "SolAdmonCreated",
                                "SolAppCreatedDeceasedDtls",
                                "SolAppCreatedSolicitorDtls",
                                "SolAppUpdated",
                                "SolProbateCreated",
                                "SolIntestacyCreated",
                                "Stopped"
                              ]
                            }
                          },
                          {
                            "range": {
                              "last_modified": {
                                "gte": "1900-01-01",
                                "lte": "2024-09-04"
                              }
                            }
                          }
                        ]
                      }
                    },
                    {
                      "bool": {
                        "filter": [
                          { "term": { "case_type_id.keyword": "Caveat" } },
                          {
                            "terms": {
                              "state.keyword": [
                                "PAAppCreated",
                                "SolAppCreated"
                              ]
                            }
                          },
                          {
                            "range": {
                              "last_modified": {
                                "gte": "1900-01-01",
                                "lte": "2024-09-04"
                              }
                            }
                          }
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
