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
        ElasticSearchQuery elasticSearchQuery = ElasticSearchQuery.builder()
            .initialSearch(true)
            .size(QUERY_SIZE)
            .build();
        String query = elasticSearchQuery.getQuery();

        assertEquals("""
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
                                "CasePaymentFailed",
                                "SolAdmonCreated",
                                "SolAppCreatedDeceasedDtls",
                                "SolAppCreatedSolicitorDtls",
                                "SolAppUpdated",
                                "SolProbateCreated",
                                "SolIntestacyCreated"
                              ]
                            }
                          },
                          {
                            "range": {
                              "last_modified": {
                                "gte": "2024-08-17",
                                "lte": "2024-08-19"
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
                                "SolAppCreated",
                                "SolAppUpdated"
                              ]
                            }
                          },
                          {
                            "range": {
                              "last_modified": {
                                "gte": "2024-08-17",
                                "lte": "2024-08-19"
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
                                "SolIntestacyCreated"
                              ]
                            }
                          },
                          {
                            "range": {
                              "last_modified": {
                                "gte": "2024-08-17",
                                "lte": "2024-08-19"
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
                                "SolAppCreated",
                                "SolAppUpdated"
                              ]
                            }
                          },
                          {
                            "range": {
                              "last_modified": {
                                "gte": "2024-08-17",
                                "lte": "2024-08-19"
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
            "size": 100,
            "sort": [
                {
                    "reference.keyword": "asc"
                }
            ],\"search_after\": [1677777777]
            }""", query);
    }
}
