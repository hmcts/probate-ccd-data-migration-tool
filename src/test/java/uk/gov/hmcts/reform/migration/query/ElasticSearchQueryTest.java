package uk.gov.hmcts.reform.migration.query;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static junit.framework.TestCase.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class ElasticSearchQueryTest {

    private static final int QUERY_SIZE = 100;

    @Test
    public void shouldReturnQuery() {
        ElasticSearchQuery elasticSearchQuery =  ElasticSearchQuery.builder()
            .initialSearch(true)
            .size(QUERY_SIZE)
            .build();
        String query = elasticSearchQuery.getQuery();
        assertEquals("""
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
                                      "lte": "2023-10-27T14:15:00"
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
          "size": 100,
          "sort": [
            {
              "reference.keyword": "asc"
            }
          ]

            }""", query);
    }

    @Test
    public void shouldReturnSearchAfterQuery() {
        ElasticSearchQuery elasticSearchQuery =  ElasticSearchQuery.builder()
            .initialSearch(false)
            .size(QUERY_SIZE)
            .searchAfterValue("1677777777")
            .build();
        String query = elasticSearchQuery.getQuery();
        assertEquals("""
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
                                      "lte": "2023-10-27T14:15:00"
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
          "size": 100,
          "sort": [
            {
              "reference.keyword": "asc"
            }
          ]
        ,\"search_after\": [1677777777]
            }""", query);
    }
}
