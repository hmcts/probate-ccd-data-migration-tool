package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Dtspb5112PaymentServiceTest {

    @Test
    void shouldUseSuccessfulPaymentStatusAndDateUpdated() {
        Dtspb5112ServiceRequestClient client = mock();
        Dtspb5112Config config = mock();
        MigrationEvent event = paymentEvent(123L);

        when(config.getPaymentServiceName()).thenReturn("probate");
        when(client.retrievePayments("Bearer user-token", "s2s-token", "probate", "123"))
            .thenReturn(paymentsResponse(
                payment("failed", "2026-01-30T12:00:00Z"),
                payment("SUCCESS", "2026-01-31T12:00:00Z")
            ));

        Dtspb5112PaymentService service = new Dtspb5112PaymentService(client, config);

        assertThat(service.hasSuccessfulPayment(event)).isTrue();
        assertThat(service.successfulPaymentDate(event))
            .contains(LocalDate.of(2026, 1, 31));

        verify(client).retrievePayments("Bearer user-token", "s2s-token", "probate", "123");
    }

    @Test
    void shouldReturnNoSuccessfulPaymentWhenOnlyFailedPaymentsExist() {
        Dtspb5112ServiceRequestClient client = mock();
        Dtspb5112Config config = mock();
        MigrationEvent event = paymentEvent(123L);

        when(config.getPaymentServiceName()).thenReturn("probate");
        when(client.retrievePayments("Bearer user-token", "s2s-token", "probate", "123"))
            .thenReturn(paymentsResponse(payment("failed", "2026-01-31T12:00:00Z")));

        Dtspb5112PaymentService service = new Dtspb5112PaymentService(client, config);

        assertThat(service.hasSuccessfulPayment(event)).isFalse();
        assertThat(service.successfulPaymentDate(event)).isEmpty();
    }

    @Test
    void shouldRecogniseSuccessfulPaymentEvenWhenDateUpdatedIsMissing() {
        Dtspb5112ServiceRequestClient client = mock();
        Dtspb5112Config config = mock();

        Dtspb5112PaymentDto payment = new Dtspb5112PaymentDto();
        payment.setStatus("success");

        when(config.getPaymentServiceName()).thenReturn("probate");
        when(client.retrievePayments("Bearer user-token", "s2s-token", "probate", "123"))
            .thenReturn(paymentsResponse(payment));

        Dtspb5112PaymentService service = new Dtspb5112PaymentService(client, config);

        MigrationEvent event = paymentEvent(123L);
        assertThat(service.hasSuccessfulPayment(event)).isTrue();
        assertThat(service.successfulPaymentDate(event)).isEmpty();
    }

    private MigrationEvent paymentEvent(long reference) {
        MigrationEvent event = mock();
        UserToken userToken = mock();
        S2sToken s2sToken = mock();

        when(event.caseSummary()).thenReturn(new CaseSummary(reference, CaseType.CAVEAT));
        when(event.userToken()).thenReturn(userToken);
        when(event.s2sToken()).thenReturn(s2sToken);
        when(userToken.getBearerToken()).thenReturn("Bearer user-token");
        when(s2sToken.s2sToken()).thenReturn("s2s-token");

        return event;
    }

    private Dtspb5112PaymentsResponse paymentsResponse(Dtspb5112PaymentDto... payments) {
        Dtspb5112PaymentsResponse response = new Dtspb5112PaymentsResponse();
        response.setPayments(List.of(payments));
        return response;
    }

    private Dtspb5112PaymentDto payment(String status, String dateUpdated) {
        Dtspb5112PaymentDto payment = new Dtspb5112PaymentDto();
        payment.setStatus(status);
        payment.setDateUpdated(Date.from(Instant.parse(dateUpdated)));
        return payment;
    }
}
