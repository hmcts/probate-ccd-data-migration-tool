package uk.gov.hmcts.reform.migration.query;

import lombok.Builder;

@Builder
public class ElasticSearchRollbackQuery {

    private static final String START_QUERY = """
        {
            "query": {
                "bool": {
                    "must": [
                        {
                            "exists": {
                                "field": "data.channelChoice"
                            }
                        }
                    ],
                    "filter": [
                        {
                            "range": {
                                "created_date": {
                                    "gte": "%s",
                                    "lte": "%s"
                                }
                            }
                        }
                    ]
                },
                "size": %s,
                "sort": [
                    {
                        "reference.keyword": "asc"
                    }
                ]
            }
        }""";

    private static final String END_QUERY = "\n    }";

    private static final String SEARCH_AFTER = "\"search_after\": [%s]";

    private String searchAfterValue;
    private int size;
    private String startDateTime;
    private String endDateTime;
    private boolean initialSearch;

    public String getQuery() {
        if (initialSearch) {
            return getInitialQuery();
        } else {
            return getSubsequentQuery();
        }
    }

    private String getInitialQuery() {
        return String.format(START_QUERY, startDateTime, endDateTime, size) + END_QUERY;
    }

    private String getSubsequentQuery() {
        return String.format(START_QUERY, startDateTime, endDateTime, size) + ","
            + String.format(SEARCH_AFTER, searchAfterValue) + END_QUERY;
    }
}
