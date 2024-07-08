package uk.gov.hmcts.reform.migration.query;

import lombok.Builder;

@Builder
public class ElasticSearchRollbackQuery {

    private static final String START_QUERY = """
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
