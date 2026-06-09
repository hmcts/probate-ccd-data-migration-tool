package uk.gov.hmcts.reform.migration.reimpl.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5005.Dtspb5005MigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5005.Dtspb5005RollbackMigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472MigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472.Dtspb5472RollbackMigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final Optional<Set<CaseSummary>> casesToRestrictTo;
    private final int querySize;
    private final boolean dryRun;

    public ReimplConfig(
            @Value("${default.thread.limit}")
            final int defaultThreadlimit,
            @Value("${migration.reimpl.id}")
            final String migrationId,
            @Value("${migration.reimpl.user_token_refresh_margin_mins}")
            final long userTokenRefreshMarginMins,
            @Value("${migration.reimpl.s2s_token_refresh_margin_mins}")
            final long s2sTokenRefreshMarginMins,
            @Value("${migration.reimpl.cases_to_restrict_to}")
            final String casesToRestrictTo,
            @Value("${case-migration.elasticsearch.querySize}")
            final int querySize,
            @Value("${migration.dryrun}")
            final boolean dryRun) {
        this.defaultThreadlimit = defaultThreadlimit;
        this.migrationId = Objects.requireNonNull(migrationId);
        this.userTokenRefreshMargin = Duration.ofMinutes(userTokenRefreshMarginMins);
        this.s2sTokenRefreshMargin = Duration.ofMinutes(s2sTokenRefreshMarginMins);
        this.casesToRestrictTo = processCasesToFilterTo(casesToRestrictTo);
        this.querySize = querySize;
        this.dryRun = dryRun;
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

    public Optional<Set<CaseSummary>> getCasesToRestrictTo() {
        return casesToRestrictTo;
    }

    public int getQuerySize() {
        return querySize;
    }

    public boolean isDryRun() {
        return dryRun;
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
        final Dtspb5005MigrationHandler dtspb5005MigrationHandler,
        final Dtspb5005RollbackMigrationHandler dtspb5005RollbackMigrationHandler,
        final Dtspb5472MigrationHandler dtspb5472MigrationHandler,
        final Dtspb5472RollbackMigrationHandler dtspb5472RollbackMigrationHandler
    ) {
        return Map.of(
            "DTSPB-5005", dtspb5005MigrationHandler,
            "DTSPB-5005_rollback", dtspb5005RollbackMigrationHandler,
            "DTSPB-5472", dtspb5472MigrationHandler,
            "DTSPB-5472_rollback", dtspb5472RollbackMigrationHandler);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    static Optional<Set<CaseSummary>> processCasesToFilterTo(final String casesToRestrictTo) {
        if (StringUtils.isBlank(casesToRestrictTo)) {
            log.info("Returning empty optional for casesToRestrictTo input [{}]", casesToRestrictTo);
            return Optional.empty();
        }
        Set<CaseSummary> casesToFilter = new HashSet<>();
        final String[] splitCasesToRestrictTo = casesToRestrictTo
                .trim()
                .split(",");
        for (String splitCase : splitCasesToRestrictTo) {
            final String splitCaseTrimmed = splitCase.trim();
            final String[] splitInput = splitCaseTrimmed.split(":");
            if (splitInput.length != 2) {
                final String errMessage = new StringBuilder()
                        .append("Error reading configuration for casesToFilterTo. Found entry [")
                        .append(splitCase)
                        .append("] without ':' separator. Full input was [")
                        .append(casesToRestrictTo)
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
                casesToFilter.add(caseSummary);
            } catch (RuntimeException e) {
                final String errMessage = new StringBuilder()
                    .append("Error reading configuration for casesToFilterTo. Found caseId [")
                    .append(caseIdStr)
                    .append("] and caseType [")
                    .append(caseTypeStr)
                    .append("] which could not be mapped to CaseSummary. Full input was [")
                    .append(casesToRestrictTo)
                    .append("]")
                    .toString();
                log.error(errMessage, e);
                throw new IllegalArgumentException(errMessage, e);
            }
        }
        return Optional.of(casesToFilter);
    }
}
