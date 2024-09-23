package uk.gov.hmcts.reform.migration.query;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static junit.framework.TestCase.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class ElasticSearchRollbackQueryTest {

    private static final int QUERY_SIZE = 100;
    private static String START_DATETIME = "2023-02-24T14:00:00";
    private static String EBD_DATETIME = "2023-02-25T16:00:00";

    @Test
    public void shouldReturnQuery() {
        ElasticSearchRollbackQuery elasticSearchQuery = ElasticSearchRollbackQuery.builder()
            .initialSearch(true)
            .startDateTime(START_DATETIME)
            .endDateTime(EBD_DATETIME)
            .size(QUERY_SIZE)
            .build();
        String query = elasticSearchQuery.getQuery();
        assertEquals("""
            {
                "query": {
                    "bool": {
                        "must_not": [
                            {
                                "term": {
                                    "state.keyword": "Deleted"
                                }
                            }
                        ],
                        "must": [
                            {
                                "term": {
                                    "data.applicationType.keyword": "Solicitor"
                                }
                            },
                            {
                                "term": {
                                    "data.paperForm": "Yes"
                                }
                            },
                            {
                                "exists": {
                                    "field": "data.applicantOrganisationPolicy"
                                }
                            }
                        ],
                        "filter": [
                            {
                                "range": {
                                    "last_modified": {
                                        "gte": "2023-02-24T14:00:00",
                                        "lte": "2023-02-25T16:00:00"
                                    }
                                }
                            },
                            {
                                "bool": {
                                    "should": [
                                        {
                                            "bool" : {
                                                "must": [
                                                     {"term": { "case_type_id.keyword": "GrantOfRepresentation" }},
                                                     {"term": {"data.channelChoice.keyword": "BulkScan"}}
                                                ],
                                                "must_not": [
                                                    {"term": { "state.keyword": "BOGrantIssued" }},
                                                    {"term": { "state.keyword": "BOCaseClosed"}}
                                                ]
                                            }
                                        },
                                        {
                                            "bool" : {
                                                "must": [
                                                     {"term": { "case_type_id.keyword": "Caveat" }},
                                                     {"exists" : {"field" : "data.solsSolicitorFirmName"}}
                                                ],
                                                "must_not": [
                                                    {"term": { "state.keyword": "CaveatClosed" }}
                                                ]
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
        ElasticSearchRollbackQuery elasticSearchQuery = ElasticSearchRollbackQuery.builder()
            .initialSearch(false)
            .startDateTime(START_DATETIME)
            .endDateTime(EBD_DATETIME)
            .size(QUERY_SIZE)
            .searchAfterValue("1677777777")
            .build();
        String query = elasticSearchQuery.getQuery();
        assertEquals("""
            {
                "query": {
                    "bool": {
                        "must_not": [
                            {
                                "term": {
                                    "state.keyword": "Deleted"
                                }
                            }
                        ],
                        "must": [
                            {
                                "term": {
                                    "data.applicationType.keyword": "Solicitor"
                                }
                            },
                            {
                                "term": {
                                    "data.paperForm": "Yes"
                                }
                            },
                            {
                                "exists": {
                                    "field": "data.applicantOrganisationPolicy"
                                }
                            }
                        ],
                        "filter": [
                            {
                                "range": {
                                    "last_modified": {
                                        "gte": "2023-02-24T14:00:00",
                                        "lte": "2023-02-25T16:00:00"
                                    }
                                }
                            },
                            {
                                "bool": {
                                    "should": [
                                        {
                                            "bool" : {
                                                "must": [
                                                     {"term": { "case_type_id.keyword": "GrantOfRepresentation" }},
                                                     {"term": {"data.channelChoice.keyword": "BulkScan"}}
                                                ],
                                                "must_not": [
                                                    {"term": { "state.keyword": "BOGrantIssued" }},
                                                    {"term": { "state.keyword": "BOCaseClosed"}}
                                                ]
                                            }
                                        },
                                        {
                                            "bool" : {
                                                "must": [
                                                     {"term": { "case_type_id.keyword": "Caveat" }},
                                                     {"exists" : {"field" : "data.solsSolicitorFirmName"}}
                                                ],
                                                "must_not": [
                                                    {"term": { "state.keyword": "CaveatClosed" }}
                                                ]
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
                ],"search_after": [1677777777]
                }""", query);
    }
}
