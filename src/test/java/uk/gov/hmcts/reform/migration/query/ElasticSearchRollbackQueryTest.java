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
                        "must": [
                            {
                                "exists": {
                                    "field": "data.boHandoffReasonList"
                                }
                            }
                        ],
                        "should": [
                            {"match": { "state": "CaseCreated" }},
                            {"match": { "state": "CasePaymentFailed" }},
                            {"match": { "state": "Stopped" }},
                            {"match": { "state": "Dormant" }},
                            {"match": { "state": "CasePrinted" }},
                            {"match": { "state": "BOCaseStopped" }},
                            {"match": { "state": "BOCaveatPermenant" }},
                            {"match": { "state": "BORegistrarEscalation" }},
                            {"match": { "state": "BOReadyToIssue" }},
                            {"match": { "state": "BOCaseQA" }},
                            {"match": { "state": "BOCaseMatchingIssueGrant" }},
                            {"match": { "state": "BOCaseClosed" }},
                            {"match": { "state": "BOCaseImported" }},
                            {"match": { "state": "BOExaminingReissue" }},
                            {"match": { "state": "BOCaseMatchingReissue" }},
                            {"match": { "state": "BOCaseStoppedReissue" }},
                            {"match": { "state": "BOCaseStoppedAwaitRedec" }},
                            {"match": { "state": "BORedecNotificationSent" }},
                            {"match": { "state": "BOSotGenerated" }},
                            {"match": { "state": "BOCaseWorkerEscalation" }}
                        ],
                        "filter": [
                            {
                                "range": {
                                    "last_modified": {
                                        "gte": "2023-02-24T14:00:00",
                                        "lte": "2023-02-25T16:00:00"
                                    }
                                }
                            }
                        ]
                    }
                },
                "_source": ["reference", "data.caseHandedOffToLegacySite"],
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
                        "must": [
                            {
                                "exists": {
                                    "field": "data.boHandoffReasonList"
                                }
                            }
                        ],
                        "should": [
                            {"match": { "state": "CaseCreated" }},
                            {"match": { "state": "CasePaymentFailed" }},
                            {"match": { "state": "Stopped" }},
                            {"match": { "state": "Dormant" }},
                            {"match": { "state": "CasePrinted" }},
                            {"match": { "state": "BOCaseStopped" }},
                            {"match": { "state": "BOCaveatPermenant" }},
                            {"match": { "state": "BORegistrarEscalation" }},
                            {"match": { "state": "BOReadyToIssue" }},
                            {"match": { "state": "BOCaseQA" }},
                            {"match": { "state": "BOCaseMatchingIssueGrant" }},
                            {"match": { "state": "BOCaseClosed" }},
                            {"match": { "state": "BOCaseImported" }},
                            {"match": { "state": "BOExaminingReissue" }},
                            {"match": { "state": "BOCaseMatchingReissue" }},
                            {"match": { "state": "BOCaseStoppedReissue" }},
                            {"match": { "state": "BOCaseStoppedAwaitRedec" }},
                            {"match": { "state": "BORedecNotificationSent" }},
                            {"match": { "state": "BOSotGenerated" }},
                            {"match": { "state": "BOCaseWorkerEscalation" }}
                        ],
                        "filter": [
                            {
                                "range": {
                                    "last_modified": {
                                        "gte": "2023-02-24T14:00:00",
                                        "lte": "2023-02-25T16:00:00"
                                    }
                                }
                            }
                        ]
                    }
                },
                "_source": ["reference", "data.caseHandedOffToLegacySite"],
                "size": 100,
                "sort": [
                    {
                        "reference.keyword": "asc"
                    }
                ],"search_after": [1677777777]
                }""", query);
    }
}
