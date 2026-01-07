package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5005;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
public class Dtspb5005ElasticQueries {

    private static final String BASE_GOR_MIGRATION_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": [
                        {
                            "terms": {
                                "data.applicationType.keyword": [
                                    "Solicitor"
                                ]
                            }
                        },
                        {
                            "terms": {
                                "data.channelChoice.keyword": [
                                    "PaperForm"
                                ]
                            }
                        },
                        {
                            "range": {
                                "created_date": {
                                    "gte": "2025-01-01T00:00:00Z"
                                }
                            }
                        }
                    ],
                    "must_not": [
                        {
                            "exists": {
                                "field": "data.applicantOrganisationPolicy"
                            }
                        }
                    ]
                }
            },
            "_source": ["reference"],
            "size": 0,
            "sort": [
                {
                    "reference.keyword": "asc"
                }
            ]
        }
        """;

    private static final String BASE_CAVEAT_MIGRATION_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": [
                        {
                            "terms": {
                                "data.applicationType.keyword": [
                                    "Solicitor"
                                ]
                            }
                        },
                        {
                            "terms": {
                                "data.paperForm": [
                                    "Yes"
                                ]
                            }
                        },
                        {
                            "range": {
                                "created_date": {
                                    "gte": "2025-01-01T00:00:00Z"
                                }
                            }
                        }
                    ],
                    "must_not": [
                        {
                            "exists": {
                                "field": "data.applicantOrganisationPolicy"
                            }
                        }
                    ]
                }
            },
            "_source": ["reference"],
            "size": 0,
            "sort": [
                {
                    "reference.keyword": "asc"
                }
            ]
        }
        """;

    private static final String BASE_GOR_ROLLBACK_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": [
                        {
                            "terms": {
                                "data.applicationType.keyword": [
                                    "Solicitor"
                                ]
                            }
                        },
                        {
                            "terms": {
                                "data.channelChoice.keyword": [
                                    "PaperForm"
                                ]
                            }
                        },
                        {
                            "range": {
                                "created_date": {
                                    "gte": "2025-01-01T00:00:00Z"
                                }
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
            "size": 0,
            "sort": [
                {
                    "reference.keyword": "asc"
                }
            ]
        }
        """;

    private static final String BASE_CAVEAT_ROLLBACK_QUERY = """
        {
            "query": {
                "bool": {
                    "filter": [
                        {
                            "terms": {
                                "data.applicationType.keyword": [
                                    "Solicitor"
                                ]
                            }
                        },
                        {
                            "terms": {
                                "data.paperForm": [
                                    "Yes"
                                ]
                            }
                        },
                        {
                            "range": {
                                "created_date": {
                                    "gte": "2025-01-01T00:00:00Z"
                                }
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
            "size": 0,
            "sort": [
                {
                    "reference.keyword": "asc"
                }
            ]
        }
        """;

    public JSONObject getGorMigrationQuery(
            final Integer size,
            final Optional<Long> fromReference) {
        JSONObject migrationQuery = new JSONObject(BASE_GOR_MIGRATION_QUERY);

        final JSONObject sizedQuery = addSize(migrationQuery, size);
        final JSONObject searchAfterQuery = addSearchAfter(sizedQuery, fromReference);

        log.debug("GoR migration query: {}", searchAfterQuery);
        return searchAfterQuery;
    }

    public JSONObject getCaveatMigrationQuery(
            final Integer size,
            final Optional<Long> fromReference) {
        JSONObject migrationQuery = new JSONObject(BASE_CAVEAT_MIGRATION_QUERY);

        final JSONObject sizedQuery = addSize(migrationQuery, size);
        final JSONObject searchAfterQuery = addSearchAfter(sizedQuery, fromReference);

        log.debug("Caveat migration query: {}", searchAfterQuery);
        return searchAfterQuery;
    }

    public JSONObject getGorRollbackQuery(
            final Integer size,
            final LocalDate migrationDate,
            final Optional<Long> fromReference) {
        JSONObject rollbackQuery = new JSONObject(BASE_GOR_ROLLBACK_QUERY);

        final JSONObject sizedQuery = addSize(rollbackQuery, size);
        final JSONObject searchAfterQuery = addSearchAfter(sizedQuery, fromReference);
        final JSONObject lastModifiedQuery = addLastModifiedFilter(searchAfterQuery, migrationDate);

        log.debug("GoR rollback query: {}", lastModifiedQuery);
        return lastModifiedQuery;
    }

    public JSONObject getCaveatRollbackQuery(
            final Integer size,
            final LocalDate migrationDate,
            final Optional<Long> fromReference) {
        JSONObject rollbackQuery = new JSONObject(BASE_CAVEAT_ROLLBACK_QUERY);

        final JSONObject sizedQuery = addSize(rollbackQuery, size);
        final JSONObject searchAfterQuery = addSearchAfter(sizedQuery, fromReference);
        final JSONObject lastModifiedQuery = addLastModifiedFilter(searchAfterQuery, migrationDate);

        log.debug("Caveat rollback query: {}", lastModifiedQuery);
        return lastModifiedQuery;
    }

    JSONObject addSize(
            final JSONObject query,
            final Integer size) {
        Objects.requireNonNull(size);

        if (size <= 0) {
            log.error("Requested query size must be greater than zero, found {}", size);
            throw new IllegalArgumentException("Requested query size must be greater than zero");
        }
        query.put("size", size);

        return query;
    }

    JSONObject addSearchAfter(
            final JSONObject query,
            final Optional<Long> fromReference) {
        Objects.requireNonNull(fromReference);

        if (fromReference.isPresent()) {
            final Long reference = fromReference.get();
            final JSONArray fromRef = new JSONArray();
            fromRef.put(reference);
            query.put("search_after", fromRef);
        }

        return query;
    }

    JSONObject addLastModifiedFilter(
            final JSONObject rollbackQuery,
            final LocalDate migrationDate) {
        Objects.requireNonNull(migrationDate);

        final JSONObject lastModifiedFilter = new JSONObject();
        final JSONObject range = new JSONObject();
        final JSONObject lastModified = new JSONObject();
        final String migrationDateStr = migrationDate.toString();

        lastModifiedFilter.put("range", range);
        range.put("last_modified", lastModified);
        lastModified.put("gte", migrationDateStr);

        final JSONObject query = rollbackQuery.getJSONObject("query");
        final JSONObject bool = query.getJSONObject("bool");
        final JSONArray filters = bool.getJSONArray("filter");
        filters.put(lastModifiedFilter);

        return rollbackQuery;
    }
}
