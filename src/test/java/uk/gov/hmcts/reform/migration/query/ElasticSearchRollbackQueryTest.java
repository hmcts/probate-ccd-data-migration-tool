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
                                "match": {
                                    "state": "Deleted"
                                }
                            },
                            {
                                "match": {
                                    "state": "BOGrantIssued"
                                }
                            }
                        ],
                        "must": [
                            {
                                "term": {
                                    "data.paperForm.keyword": "Yes"
                                }
                            },
                            {
                                "term": {
                                    "data.applicationType.keyword": "Solicitor"
                                }
                            },
                            {
                                "exists": {
                                    "field": "data.applicantOrganisationPolicy"
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
                                "match": {
                                    "state": "Deleted"
                                }
                            },
                            {
                                "match": {
                                    "state": "BOGrantIssued"
                                }
                            }
                        ],
                        "must": [
                            {
                                "term": {
                                    "data.paperForm.keyword": "Yes"
                                }
                            },
                            {
                                "term": {
                                    "data.applicationType.keyword": "Solicitor"
                                }
                            },
                            {
                                "exists": {
                                    "field": "data.applicantOrganisationPolicy"
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
