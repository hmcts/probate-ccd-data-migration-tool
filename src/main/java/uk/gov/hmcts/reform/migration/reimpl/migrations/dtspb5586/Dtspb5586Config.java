package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5586;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.time.LocalDate;

@Configuration
@PropertySource("classpath:application.properties")
public class Dtspb5586Config {
    private final LocalDate rollbackDate;

    public Dtspb5586Config(

            @Value("${dtspb5586.rollback_date}")
            final String rollbackDate) {
        this.rollbackDate = LocalDate.parse(rollbackDate);
    }

    public LocalDate getRollbackDate() {
        return rollbackDate;
    }
}
