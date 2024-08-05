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
                            { "match": { "state": "Deleted" }},
                            { "match": { "state": "Pending" }},
                            { "match": { "state": "SolAdmonCreated" }},
                            { "match": { "state": "SolAppCreatedDeceasedDtls" }},
                            { "match": { "state": "SolAppCreatedSolicitorDtls" }},
                            { "match": { "state": "SolAppUpdated" }},
                            { "match": { "state": "CaseCreated" }},
                            { "match": { "state": "BOCaseClosed" }},
                            { "match": { "state": "CasePaymentFailed" }},
                            { "match": { "state": "SolProbateCreated" }},
                            { "match": { "state": "SolIntestacyCreated" }},
                            { "match": { "state": "Stopped" }},
                            { "match": { "state": "PAAppCreated" }}
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
                                        {"match": { "state": "BOCaseMatchingExamining" }},
                                        {"match": { "state": "BOCaseMatchingIssueGrant" }},
                                        {"match": { "state": "BOCaseQA" }},
                                        {"match": { "state": "BOReadyToIssue" }},
                                        {"match": { "state": "BORegistrarEscalation" }},
                                        {"match": { "state": "BOCaseStopped" }},
                                        {"match": { "state": "CasePrinted" }},
                                        {"match": { "state": "BOSotGenerated" }},
                                        {"match": { "state": "BORedecNotificationSent" }},
                                        {"match": { "state": "BOCaseStoppedAwaitRedec" }},
                                        {"match": { "state": "BOCaseStoppedReissue" }},
                                        {"match": { "state": "BOCaseMatchingReissue" }},
                                        {"match": { "state": "BOExaminingReissue" }},
                                        {"match": { "state": "BOCaseImported" }},
                                        {"match": { "state": "BOCaveatPermenant" }},
                                        {"match": { "state": "BOCaseWorkerEscalation" }},
                                        {"match": { "state": "Dormant" }},
                                        {"match": { "state": "BOPostGrantIssued" }}
                                    ]
                                }
                            }
                        ]
                    }
                },
                "_source": ["reference", "state"],
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
                            { "match": { "state": "Deleted" }},
                            { "match": { "state": "Pending" }},
                            { "match": { "state": "SolAdmonCreated" }},
                            { "match": { "state": "SolAppCreatedDeceasedDtls" }},
                            { "match": { "state": "SolAppCreatedSolicitorDtls" }},
                            { "match": { "state": "SolAppUpdated" }},
                            { "match": { "state": "CaseCreated" }},
                            { "match": { "state": "BOCaseClosed" }},
                            { "match": { "state": "CasePaymentFailed" }},
                            { "match": { "state": "SolProbateCreated" }},
                            { "match": { "state": "SolIntestacyCreated" }},
                            { "match": { "state": "Stopped" }},
                            { "match": { "state": "PAAppCreated" }}
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
                                        {"match": { "state": "BOCaseMatchingExamining" }},
                                        {"match": { "state": "BOCaseMatchingIssueGrant" }},
                                        {"match": { "state": "BOCaseQA" }},
                                        {"match": { "state": "BOReadyToIssue" }},
                                        {"match": { "state": "BORegistrarEscalation" }},
                                        {"match": { "state": "BOCaseStopped" }},
                                        {"match": { "state": "CasePrinted" }},
                                        {"match": { "state": "BOSotGenerated" }},
                                        {"match": { "state": "BORedecNotificationSent" }},
                                        {"match": { "state": "BOCaseStoppedAwaitRedec" }},
                                        {"match": { "state": "BOCaseStoppedReissue" }},
                                        {"match": { "state": "BOCaseMatchingReissue" }},
                                        {"match": { "state": "BOExaminingReissue" }},
                                        {"match": { "state": "BOCaseImported" }},
                                        {"match": { "state": "BOCaveatPermenant" }},
                                        {"match": { "state": "BOCaseWorkerEscalation" }},
                                        {"match": { "state": "Dormant" }},
                                        {"match": { "state": "BOPostGrantIssued" }}
                                    ]
                                }
                            }
                        ]
                    }
                },
                "_source": ["reference", "state"],
                "size": 100,
                "sort": [
                    {
                        "reference.keyword": "asc"
                    }
                ],"search_after": [1677777777]
                }""", query);
    }
}
