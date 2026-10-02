package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.time.LocalDate;

@Configuration
@PropertySource("classpath:application.properties")
public class Dtspb5112Config {
    private final LocalDate rollbackDate;
    private final String paymentServiceName;

    public Dtspb5112Config(
        @Value("${dtspb5112.rollback_date}") final String rollbackDate,
        @Value("${dtspb5112.payment_service_name:probate}") final String paymentServiceName
    ) {
        this.rollbackDate = LocalDate.parse(rollbackDate);
        this.paymentServiceName = paymentServiceName;
    }

    public LocalDate getRollbackDate() {
        return rollbackDate;
    }

    public String getPaymentServiceName() {
        return paymentServiceName;
    }
}
