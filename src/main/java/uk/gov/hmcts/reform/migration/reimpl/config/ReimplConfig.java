package uk.gov.hmcts.reform.migration.reimpl.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472RollbackMigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5539.Dtspb5539MigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@PropertySource("classpath:application.properties")
@Configuration
@Slf4j
public class ReimplConfig {
    private final int defaultThreadlimit;
    private final String migrationId;
    private final Duration userTokenRefreshMargin;
    private final Duration s2sTokenRefreshMargin;
    private final Optional<Set<CaseSummary>> casesToExclude;
    private final Optional<Set<CaseSummary>> casesToMigrate;
    private final Optional<Set<CaseSummary>> casesToRestrictTo;
    private final int querySize;
    private final boolean dryRun;
    private final boolean initialRun;
    private final int initialSize;

    public ReimplConfig(
            @Value("${default.thread.limit}")
            final int defaultThreadlimit,
            @Value("${migration.reimpl.id}")
            final String migrationId,
            @Value("${migration.reimpl.user_token_refresh_margin_mins}")
            final long userTokenRefreshMarginMins,
            @Value("${migration.reimpl.s2s_token_refresh_margin_mins}")
            final long s2sTokenRefreshMarginMins,
            @Value("${migration.reimpl.cases_to_exclude:}")
            final String casesToExclude,
            @Value("${migration.reimpl.cases_to_migrate:}")
            final String casesToMigrate,
            @Value("${migration.reimpl.cases_to_restrict_to:}")
            final String casesToRestrictTo,
            @Value("${case-migration.elasticsearch.querySize}")
            final int querySize,
            @Value("${migration.dryrun}")
            final boolean dryRun,
            @Value("${migration.reimpl.initial_run:}")
            final boolean initialRun,
            @Value("${migration.reimpl.initial_size:}")
            final int initialSize) {
        if (initialRun && initialSize <= 0) {
            throw new IllegalArgumentException(
                "MIGRATION_INITIAL_SIZE must be greater than zero when MIGRATION_INITIAL_RUN is enabled"
            );
        }
        this.defaultThreadlimit = defaultThreadlimit;
        this.migrationId = Objects.requireNonNull(migrationId);
        this.userTokenRefreshMargin = Duration.ofMinutes(userTokenRefreshMarginMins);
        this.s2sTokenRefreshMargin = Duration.ofMinutes(s2sTokenRefreshMarginMins);
        this.casesToExclude = processCasesConfig(casesToExclude);
        this.casesToMigrate = processCasesConfig(casesToMigrate);
        this.casesToRestrictTo = processCasesConfig(casesToRestrictTo);
        this.querySize = querySize;
        this.dryRun = dryRun;
        this.initialRun = initialRun;
        this.initialSize = initialSize;
        validateCaseSelectionConfiguration();
    }

    public String getMigrationId() {
        return migrationId;
    }

    public Duration getUserTokenRefreshMargin() {
        return userTokenRefreshMargin;
    }

    public Duration getS2sTokenRefreshMargin() {
        return s2sTokenRefreshMargin;
    }

    public Optional<Set<CaseSummary>> getCasesToExclude() {
        return casesToExclude;
    }

    public Optional<Set<CaseSummary>> getCasesToMigrate() {
        return casesToMigrate;
    }

    public Optional<Set<CaseSummary>> getCasesToRestrictTo() {
        return casesToRestrictTo;
    }

    public int getQuerySize() {
        return querySize;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public OptionalInt getMaximumResults() {
        return initialRun
            ? OptionalInt.of(initialSize)
            : OptionalInt.empty();
    }

    /**
     * Creates and returns a NEW ExecutorService on each call. The caller MUST close it (e.g. by using a
     * try-with-resources when requesting it).
     * @return A new ExecutorService instance which will accept tasks.
     */
    public ExecutorService getNewExecutor() {
        return Executors.newFixedThreadPool(defaultThreadlimit);
    }

    @Bean
    public Map<String, MigrationHandler> migrationHandlers(
        final Dtspb5472MigrationHandler dtspb5472MigrationHandler,
        final Dtspb5472RollbackMigrationHandler dtspb5472RollbackMigrationHandler,
        final Dtspb5539MigrationHandler dtspb5539MigrationHandler
    ) {
        return Map.ofEntries(
            Map.entry("DTSPB-5472", dtspb5472MigrationHandler),
            Map.entry("DTSPB-5472_rollback", dtspb5472RollbackMigrationHandler),
            Map.entry("DTSPB-5539", dtspb5539MigrationHandler)
            );
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    static Optional<Set<CaseSummary>> processCasesConfig(final String casesConfig) {
        if (StringUtils.isBlank(casesConfig)) {
            log.info("Returning empty optional for cases config input [{}]", casesConfig);
            return Optional.empty();
        }
        Set<CaseSummary> caseSummarySet = new HashSet<>();
        final String[] splitCasesConfig = casesConfig
                .trim()
                .split(",");
        for (String splitCase : splitCasesConfig) {
            final String splitCaseTrimmed = splitCase.trim();
            final String[] splitInput = splitCaseTrimmed.split(":");
            if (splitInput.length != 2) {
                final String errMessage = new StringBuilder()
                        .append("Error reading configuration for cases config. Found entry [")
                        .append(splitCase)
                        .append("] without ':' separator. Full input was [")
                        .append(casesConfig)
                        .append("]")
                        .toString();
                log.error(errMessage);
                throw new IllegalArgumentException(errMessage);
            }

            final String caseIdStr = splitInput[0].trim();
            final String caseTypeStr = splitInput[1].trim();
            try {
                final Long caseId = Long.parseLong(caseIdStr);
                final CaseType caseType = CaseType.fromCcdValue(caseTypeStr);
                final CaseSummary caseSummary = new CaseSummary(caseId, caseType);
                log.info("Adding case to filter for: [{}]", caseSummary);
                caseSummarySet.add(caseSummary);
            } catch (RuntimeException e) {
                final String errMessage = new StringBuilder()
                    .append("Error reading configuration for cases config. Found caseId [")
                    .append(caseIdStr)
                    .append("] and caseType [")
                    .append(caseTypeStr)
                    .append("] which could not be mapped to CaseSummary. Full input was [")
                    .append(casesConfig)
                    .append("]")
                    .toString();
                log.error(errMessage, e);
                throw new IllegalArgumentException(errMessage, e);
            }
        }
        return Optional.of(caseSummarySet);
    }

    private void validateCaseSelectionConfiguration() {
        if (casesToMigrate.isPresent()
            && casesToRestrictTo.isPresent()) {
            throw new IllegalArgumentException(
                "MIGRATION_CASES_TO_MIGRATE and MIGRATION_CASES_TO_RESTRICT_TO cannot both be configured"
            );
        }
    }
}
