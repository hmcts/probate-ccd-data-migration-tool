package uk.gov.hmcts.reform.migration.reimpl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import uk.gov.hmcts.reform.migration.reimpl.dtspb5005.Dtspb5005MigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.dtspb5005.Dtspb5005RollbackMigrationHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.Map;
import java.util.Objects;

@PropertySource("classpath:application.properties")
@Configuration
public class ReimplConfig {
    private final int defaultThreadlimit;
    private final String migrationId;

    public ReimplConfig(
            @Value("${default.thread.limit}")
            final int defaultThreadlimit,
            @Value("${migration.id}")
            final String migrationId) {
        this.defaultThreadlimit = defaultThreadlimit;
        this.migrationId = Objects.requireNonNull(migrationId);
    }

    public int getDefaultThreadlimit() {
        return defaultThreadlimit;
    }

    public String getMigrationId() {
        return migrationId;
    }

    @Bean
    public Map<String, MigrationHandler> migrationHandlers(
        final Dtspb5005MigrationHandler dtspb5005MigrationHandler,
        final Dtspb5005RollbackMigrationHandler dtspb5005RollbackMigrationHandler
    ) {
        return Map.of(
            "DTSPB-5005", dtspb5005MigrationHandler,
            "DTSPB-5005_rollback", dtspb5005RollbackMigrationHandler);
    }
}
