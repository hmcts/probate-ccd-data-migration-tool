package uk.gov.hmcts.reform.migration.reimpl.dtspb5064;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:application.properties")
public class Dtspb5064Config {
    private final int querySize;
    private final boolean dryRun;

    public Dtspb5064Config(
            @Value("${dtspb5064.query_size}")
            final int querySize,
            @Value("${dtspb5064.dry_run}")
            final boolean dryRun) {
        this.querySize = querySize;
        this.dryRun = dryRun;
    }

    public int getQuerySize() {
        return querySize;
    }

    public boolean isDryRun() {
        return dryRun;
    }
}
