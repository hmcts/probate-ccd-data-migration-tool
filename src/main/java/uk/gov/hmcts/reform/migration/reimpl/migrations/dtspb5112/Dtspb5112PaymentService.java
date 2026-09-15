package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class Dtspb5112PaymentService {
    private static final String SUCCESS = "success";

    private final Dtspb5112ServiceRequestClient serviceRequestClient;
    private final Dtspb5112Config config;
    private final ConcurrentMap<Long, PaymentLookup> paymentLookups = new ConcurrentHashMap<>();

    public Dtspb5112PaymentService(
        Dtspb5112ServiceRequestClient serviceRequestClient,
        Dtspb5112Config config
    ) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
    }

    public boolean hasSuccessfulPayment(MigrationEvent migrationEvent) {
        return lookup(migrationEvent).successful();
    }

    public Optional<LocalDate> successfulPaymentDate(MigrationEvent migrationEvent) {
        return lookup(migrationEvent).dateUpdated();
    }

    private PaymentLookup lookup(MigrationEvent migrationEvent) {
        Long reference = migrationEvent.caseSummary().reference();
        return paymentLookups.computeIfAbsent(reference, ignored -> retrievePayment(migrationEvent));
    }

    private PaymentLookup retrievePayment(MigrationEvent migrationEvent) {
        Dtspb5112PaymentsResponse response = serviceRequestClient.retrievePayments(
            migrationEvent.userToken().getBearerToken(),
            migrationEvent.s2sToken().s2sToken(),
            config.getPaymentServiceName(),
            migrationEvent.caseSummary().reference().toString()
        );

        List<Dtspb5112PaymentDto> payments = response == null || response.getPayments() == null
            ? List.of()
            : response.getPayments();

        boolean isPaymentSuccessful = payments.stream()
            .anyMatch(payment -> SUCCESS.equalsIgnoreCase(payment.getStatus()));

        Optional<LocalDate> paymentDate = payments.stream()
            .filter(payment -> SUCCESS.equalsIgnoreCase(payment.getStatus()))
            .map(Dtspb5112PaymentDto::getDateUpdated)
            .filter(Objects::nonNull)
            .findFirst()
            .map(date -> date.toInstant().atZone(ZoneOffset.UTC).toLocalDate());

        return new PaymentLookup(isPaymentSuccessful, paymentDate);
    }

    private record PaymentLookup(boolean successful, Optional<LocalDate> dateUpdated) {
    }
}
