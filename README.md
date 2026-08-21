# ccd-case-migration-starter

CCD Case Migration Starter provides a framework for data migrations within CCD , to assist with case migrations that are required when the case definition changes in a way that requires existing cases to be updated to match the new case definition.

The source code is maintained as a template within GitHub and is typically either cloned by a service team to establish a migration capability , or branched within the repository.

CCD Case Migration Starter framework source code is located in HMCTS GitHub repository  https://github.com/hmcts/ccd-case-migration-starter

It is built by Jenkins using HMCTS Jenkins job  https://build.platform.hmcts.net/job/HMCTS_a_to_c/job/ccd-case-migration-starter/

## Getting started

To utilise the CCD Case Migration Starter :-

1. Clone the GitHub repository and create a branch for the migration task.

2. Make the required source code changes for the migration task (see section below).

3. Create a pull request.

4. Request PlatOps to copy the JAR that was built using the pipeline from the repository to the bastion server for operation.

## Required source code changes

As a minimum , the source code changes described below should be made.

Create a Java class which implements `uk.gov.hmcts.reform.migration.service.DataMigrationService` interface in similar way as shown below :-

```java
package uk.gov.hmcts.reform.migration.service;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.function.Predicate;

@Component
public class DataMigrationServiceImpl implements DataMigrationService {
    @Override
    public Predicate<CaseDetails> accepts() {
        return true; // Predicate that allows to narrow number of cases that gets migrated
    }

    @Override
    public void migrate(CaseDetails caseDetails) {
        // Case data migration logic goes here
    }
}
```

Ensure that the application properties below are configured as required in `application.properties` file :-

```properties
idam.api.url= # IDAM API URL used to authenticate system update user (pointing to localhost version of IDAM API by default)
idam.client.id= # IDAM OAuth2 client ID used to authenticate system update user
idam.client.secret= # IDAM OAuth2 client secret used to authenticate system update user
idam.client.redirect_uri= # IDAM OAuth2 redirect URL used to authenticate system update user

idam.s2s-auth.url= # S2S API URL used to authenticate service (pointing to localhost version of S2S API by default)
idam.s2s-auth.microservice= # S2S micro service name used to authenticate service
idam.s2s-auth.totp_secret= # S2S micro service secret used to authenticate service

core_case_data.api.url= # CCD data store API URL used to fetch / update case details (pointing to localhost version of CCD by default)

migration.idam.username= # IDAM username of a system update user that performs data migration
migration.idam.password= # IDAM password of a system update user that performs data migration
migration.jurisdiction= # CCD jurisdiction that data migration is run against
migration.casetype= # CCD case type that data migration is run against
migration.caseId= # optional CCD case ID in case only one case needs to be migrated

case-migration.elasticsearch.querySize= # Elasticsearch query size limit
case-migration.processing.limit= # Migration processing size limit
```


## Re-implemented migration runner

Reimplemented migration runner supports direct case selection, candidate-query restriction, exclusions, and limited initial runs.

### Case selection

The following environment variables control which cases are migrated:

| Environment variable | Candidate query | Behaviour |
| --- | --- | --- |
| `MIGRATION_CASES_TO_MIGRATE` | Skipped | Migrates exactly the configured cases. This is the efficient option when the case references are already known. |
| `MIGRATION_CASES_TO_RESTRICT_TO` | Executed | Intersects the eligible query results with the configured cases. This is primarily intended for demo and integration testing of migration queries while limiting the cases that are changed. |
| `MIGRATION_CASES_TO_EXCLUDE` | Depends on the selection path | Removes the configured cases from the final selection after direct selection or candidate querying. |

The corresponding application properties are:

```properties
migration.reimpl.cases_to_migrate=${MIGRATION_CASES_TO_MIGRATE:}
migration.reimpl.cases_to_restrict_to=${MIGRATION_CASES_TO_RESTRICT_TO:}
migration.reimpl.cases_to_exclude=${MIGRATION_CASES_TO_EXCLUDE:}
```

`MIGRATION_CASES_TO_MIGRATE` and `MIGRATION_CASES_TO_RESTRICT_TO` cannot both be configured. The application fails during configuration when both values are present.

Case lists use the following format:

```text
case-reference:case-type,case-reference:case-type
```

For example:

```text
1234567890123456:Caveat,1234567890123457:GrantOfRepresentation
```

Case type forms part of a case's identity for selection purposes. A case reference configured with the wrong case type does not match the candidate case.

The selection order is:

1. When `MIGRATION_CASES_TO_MIGRATE` is configured, use those cases directly and skip the candidate query.
2. Otherwise, execute the migration's candidate query and optionally intersect its results with `MIGRATION_CASES_TO_RESTRICT_TO`.
3. Remove any cases configured in `MIGRATION_CASES_TO_EXCLUDE`.
4. Queue the remaining cases for migration.

### Limiting an initial run

The following properties limit page-size-aware Elasticsearch candidate searches:

```properties
# Limits each page-size-aware Elasticsearch search invocation.
# A migration searching multiple case types may apply this limit once per case type.
migration.reimpl.initial_run=${MIGRATION_INITIAL_RUN:false}
migration.reimpl.initial_size=${MIGRATION_INITIAL_SIZE:10}
```

The default is an unlimited run because `MIGRATION_INITIAL_RUN` is `false`. When it is set to `true`, each page-size-aware candidate search returns at most `MIGRATION_INITIAL_SIZE` cases.

The limit applies per search invocation, not necessarily to the migration as a whole. If a migration performs one search for each of four case types with an initial size of 10, it may retrieve up to 10 cases per case type and therefore up to 40 cases overall.

The tool logs when a limit is applied and when a search reaches that limit.

Avoid combining a small `MIGRATION_INITIAL_SIZE` with `MIGRATION_CASES_TO_RESTRICT_TO`. A configured restricted case is not selected if it falls outside the limited candidate-query results.

### Common scenarios

| Scenario | Configuration |
| --- | --- |
| Migrate every eligible case | Leave `MIGRATION_CASES_TO_MIGRATE` and `MIGRATION_CASES_TO_RESTRICT_TO` unset; leave `MIGRATION_INITIAL_RUN` as `false`. |
| Migrate up to a fixed number of eligible cases | Set `MIGRATION_INITIAL_RUN=true` and set `MIGRATION_INITIAL_SIZE` to the required per-search maximum. |
| Migrate a fixed set of known cases without running the candidate query | Set `MIGRATION_CASES_TO_MIGRATE`. |
| Verify that known cases are selected by the candidate query and migrate only those matches | Set `MIGRATION_CASES_TO_RESTRICT_TO`; normally leave the initial-run limit disabled. |
| Skip known cases from an otherwise selected set | Set `MIGRATION_CASES_TO_EXCLUDE`. |


## Unit tests

To run all unit tests please execute following command :-

```bash
    ./gradlew test
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
