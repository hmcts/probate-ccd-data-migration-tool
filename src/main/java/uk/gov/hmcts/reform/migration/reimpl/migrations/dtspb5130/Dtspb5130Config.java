package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5130;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.time.LocalDate;

@Configuration
@PropertySource("classpath:application.properties")
public class Dtspb5130Config {
    private final int querySize;
    private final boolean dryRun;
    private final LocalDate rollbackDate;

    public Dtspb5130Config(
            @Value("${dtspb5005.query_size}") //use 5005 values for 5130 for now
            final int querySize,
            @Value("${dtspb5005.dry_run}")
            final boolean dryRun,
            @Value("${dtspb5005.rollback_date}")
            final String rollbackDate) {
        this.querySize = querySize;
        this.dryRun = dryRun;
        this.rollbackDate = LocalDate.parse(rollbackDate);
    }

    public int getQuerySize() {
        return querySize;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public LocalDate getRollbackDate() {
        return rollbackDate;
    }
}
