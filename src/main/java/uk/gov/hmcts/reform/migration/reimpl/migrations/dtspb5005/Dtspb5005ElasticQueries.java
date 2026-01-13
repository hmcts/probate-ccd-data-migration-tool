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

    private static final String baseGorMigrationQuery = """
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

    private static final String baseCaveatMigrationQuery = """
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

    private static final String baseGorRollbackQuery = """
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

    private static final String baseCaveatRollbackQuery = """
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
        Objects.requireNonNull(size);
        Objects.requireNonNull(fromReference);

        if (size <= 0) {
            log.error("Requested query size must be greater than zero, found {}", size);
            throw new IllegalArgumentException("Requested query size must be greater than zero");
        }

        JSONObject migrationQuery = new JSONObject(baseGorMigrationQuery);

        migrationQuery.put("size", size);

        if (fromReference.isPresent()) {
            JSONArray fromReferenceArray = new JSONArray();
            fromReferenceArray.put(fromReference.get());
            migrationQuery.put("search_after", fromReferenceArray);
        }

        return migrationQuery;
    }

    public JSONObject getCaveatMigrationQuery(
            final Integer size,
            final Optional<Long> fromReference) {
        Objects.requireNonNull(size);
        Objects.requireNonNull(fromReference);

        if (size <= 0) {
            log.error("Requested query size must be greater than zero, found {}", size);
            throw new IllegalArgumentException("Requested query size must be greater than zero");
        }

        JSONObject migrationQuery = new JSONObject(baseCaveatMigrationQuery);

        migrationQuery.put("size", size);

        if (fromReference.isPresent()) {
            JSONArray fromReferenceArray = new JSONArray();
            fromReferenceArray.put(fromReference.get());
            migrationQuery.put("search_after", fromReferenceArray);
        }

        return migrationQuery;
    }

    public JSONObject getGorRollbackQuery(
            final Integer size,
            final LocalDate migrationDate,
            final Optional<Long> fromReference) {
        Objects.requireNonNull(size);
        Objects.requireNonNull(migrationDate);
        Objects.requireNonNull(fromReference);

        if (size <= 0) {
            log.error("Requested query size must be greater than zero, found {}", size);
            throw new IllegalArgumentException("Requested query size must be greater than zero");
        }

        JSONObject rollbackQuery = new JSONObject(baseGorRollbackQuery);

        rollbackQuery.put("size", size);

        if (fromReference.isPresent()) {
            JSONArray fromReferenceArray = new JSONArray();
            fromReferenceArray.put(fromReference.get());
            rollbackQuery.put("search_after", fromReferenceArray);
        }

        final JSONObject lastModifiedFilter = lastModifiedFilter(migrationDate);

        final JSONObject query = rollbackQuery.getJSONObject("query");
        final JSONObject bool = query.getJSONObject("bool");
        final JSONArray filters = bool.getJSONArray("filter");
        filters.put(lastModifiedFilter);

        return rollbackQuery;
    }

    public JSONObject getCaveatRollbackQuery(
            final Integer size,
            final LocalDate migrationDate,
            final Optional<Long> fromReference) {
        Objects.nonNull(size);
        Objects.requireNonNull(migrationDate);
        Objects.nonNull(fromReference);

        if (size <= 0) {
            log.error("Requested query size must be greater than zero, found {}", size);
            throw new IllegalArgumentException("Requested query size must be greater than zero");
        }

        JSONObject rollbackQuery = new JSONObject(baseCaveatRollbackQuery);

        rollbackQuery.put("size", size);

        if (fromReference.isPresent()) {
            JSONArray fromReferenceArray = new JSONArray();
            fromReferenceArray.put(fromReference.get());
            rollbackQuery.put("search_after", fromReferenceArray);
        }

        final JSONObject lastModifiedFilter = lastModifiedFilter(migrationDate);

        final JSONObject query = rollbackQuery.getJSONObject("query");
        final JSONObject bool = query.getJSONObject("bool");
        final JSONArray filters = bool.getJSONArray("filter");
        filters.put(lastModifiedFilter);

        return rollbackQuery;
    }

    JSONObject lastModifiedFilter(final LocalDate migrationDate) {
        final JSONObject lastModifiedFilter = new JSONObject();
        final JSONObject range = new JSONObject();
        final JSONObject lastModified = new JSONObject();
        final String migrationDateStr = migrationDate.toString();

        lastModifiedFilter.put("range", range);
        range.put("last_modified", lastModified);
        lastModified.put("gte", migrationDateStr);

        return lastModifiedFilter;
    }
}
