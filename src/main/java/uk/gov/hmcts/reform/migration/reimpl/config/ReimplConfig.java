package uk.gov.hmcts.reform.migration.reimpl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import uk.gov.hmcts.reform.migration.reimpl.dtspb5005.Dtspb5005MigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.dtspb5005.Dtspb5005RollbackMigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Map;
import java.util.Objects;

@PropertySource("classpath:application.properties")
@Configuration
public class ReimplConfig {
    private final int defaultThreadlimit;
    private final String migrationId;
    private final Duration userTokenRefreshMargin;
    private final Duration s2sTokenRefreshMargin;

    public ReimplConfig(
            @Value("${default.thread.limit}")
            final int defaultThreadlimit,
            @Value("${migration.reimpl.id}")
            final String migrationId,
            @Value("${migration.reimpl.user_token_refresh_margin_mins}")
            final long userTokenRefreshMarginMins,
            @Value("${migration.reimpl.s2s_token_refresh_margin_mins}")
            final long s2sTokenRefreshMarginMins) {
        this.defaultThreadlimit = defaultThreadlimit;
        this.migrationId = Objects.requireNonNull(migrationId);
        this.userTokenRefreshMargin = Duration.ofMinutes(userTokenRefreshMarginMins);
        this.s2sTokenRefreshMargin = Duration.ofMinutes(s2sTokenRefreshMarginMins);
    }

    public int getDefaultThreadlimit() {
        return defaultThreadlimit;
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

    @Bean
    public Map<String, MigrationHandler> migrationHandlers(
        final Dtspb5005MigrationHandler dtspb5005MigrationHandler,
        final Dtspb5005RollbackMigrationHandler dtspb5005RollbackMigrationHandler
    ) {
        return Map.of(
            "DTSPB-5005", dtspb5005MigrationHandler);
//        ,
//            "DTSPB-5005_rollback", dtspb5005RollbackMigrationHandler);
    }
}
