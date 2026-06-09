package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5472;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.time.LocalDate;

@Configuration
@PropertySource("classpath:application.properties")
public class Dtspb5472Config {
    private final LocalDate rollbackDate;

    public Dtspb5472Config(
            @Value("${dtspb5472.rollback_date}")
            final String rollbackDate) {
        this.rollbackDate = LocalDate.parse(rollbackDate);
    }

    public LocalDate getRollbackDate() {
        return rollbackDate;
    }
}
