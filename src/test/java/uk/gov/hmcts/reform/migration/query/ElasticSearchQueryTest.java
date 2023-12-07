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
               "must_not": {
                 {"match": { "data.bulkScanEnvelopes": "[]" }}
               },
                "must": [
                      {"match": { "data.registryLocation": "Newcastle" }}
                ],
                "must": [
                      {"match": { data.paperForm": "Yes" }}
                ],
                "must": [
                      {"match": { "data.applicationType": "Solicitor" }}
                ],
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
               "must_not": {
                 {"match": { "data.bulkScanEnvelopes": "[]" }}
               },
                "must": [
                      {"match": { "data.registryLocation": "Newcastle" }}
                ],
                "must": [
                      {"match": { data.paperForm": "Yes" }}
                ],
                "must": [
                      {"match": { "data.applicationType": "Solicitor" }}
                ],
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
