package uk.gov.hmcts.reform.migration.query;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static junit.framework.TestCase.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class ElasticSearchRollbackQueryTest {

    private static final int QUERY_SIZE = 100;

    @Test
    public void shouldReturnQuery() {
        ElasticSearchRollbackQuery elasticSearchQuery = ElasticSearchRollbackQuery.builder()
            .initialSearch(true)
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
                                    "state": "Deleted"
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
                                    "data.paperForm.keyword": "Yes"
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
                                "bool": {
                                    "should": [
                                        {
                                            "bool" : {
                                                "must": [
                                                     {"term": { "case_type_id": "GrantOfRepresentation" }},
                                                     {"term": {"data.channelChoice.keyword": "BulkScan"}}
                                                ],
                                                "must_not": [
                                                    {"term": { "state": "BOGrantIssued" }},
                                                    {"term": { "state": "BOCaseClosed"}}
                                                ]
                                            }
                                        },
                                        {
                                            "bool" : {
                                                "must": [
                                                     {"term": { "case_type_id": "Caveat" }},
                                                     {"exists" : {"field" : "data.solsSolicitorFirmName"}}
                                                ],
                                                "must_not": [
                                                    {"term": { "state": "CaveatClosed" }}
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
                                    "state": "Deleted"
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
                                    "data.paperForm.keyword": "Yes"
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
                                "bool": {
                                    "should": [
                                        {
                                            "bool" : {
                                                "must": [
                                                     {"term": { "case_type_id": "GrantOfRepresentation" }},
                                                     {"term": {"data.channelChoice.keyword": "BulkScan"}}
                                                ],
                                                "must_not": [
                                                    {"term": { "state": "BOGrantIssued" }},
                                                    {"term": { "state": "BOCaseClosed"}}
                                                ]
                                            }
                                        },
                                        {
                                            "bool" : {
                                                "must": [
                                                     {"term": { "case_type_id": "Caveat" }},
                                                     {"exists" : {"field" : "data.solsSolicitorFirmName"}}
                                                ],
                                                "must_not": [
                                                    {"term": { "state": "CaveatClosed" }}
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
