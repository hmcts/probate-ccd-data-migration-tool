package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb6221;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.time.LocalDate;

@Getter
@Configuration
@PropertySource("classpath:application.properties")
public class Dtspb6221Config {

    private final LocalDate rollbackDate;

    public Dtspb6221Config(
        @Value("${dtspb6221.rollback_date}")
        final String rollbackDate) {
        this.rollbackDate = LocalDate.parse(rollbackDate);
    }

    public LocalDate getRollbackDate() {
        return rollbackDate;
    }
}
