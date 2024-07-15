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
