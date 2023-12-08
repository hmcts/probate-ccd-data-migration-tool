package uk.gov.hmcts.reform.migration.query;

import lombok.Builder;

@Builder
public class ElasticSearchQuery {

    private static final String START_QUERY = """
        {
          "query": {
            "bool": {
              "must": [
                   {"match": { "data.applicationType": "Solicitor" }},
                   {"match": { "data.paperForm": "No" }}
              ],
               "must_not": [
                   {"exists": { "field": "data.applicantOrganisationPolicy" }},
                   {"exists": { "field": "supplementary_data" }}
              ],
              "filter":
                   [
                       {
                           "range": {
                                  "created_date": {
                                      "gte": "2023-10-25T15:30:00",
                                      "lte": "2023-10-26T14:15:00"
                                  }
                           }
                       },
                       {
                           "bool": {
                                "should":[
                                     {
                                        "bool" : {
                                            "must": [
                                                 {"match": { "case_type_id": "GrantOfRepresentation" }},
                                                 {"exists" : {"field" : "data.solsSolicitorWillSignSOT"}}
                                            ]
                                        }
                                    },
                                    {
                                        "bool" : {
                                            "must": [
                                                 {"match": { "case_type_id": "Caveat" }},
                                                 {"exists" : {"field" : "data.solsSolicitorFirmName"}}
                                            ]
                                        }
                                    }
                                ]
                           }
                       }
                   ]

            }
          },
          "size": %s,
          "sort": [
            {
              "reference.keyword": "asc"
            }
          ]
          """;

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
