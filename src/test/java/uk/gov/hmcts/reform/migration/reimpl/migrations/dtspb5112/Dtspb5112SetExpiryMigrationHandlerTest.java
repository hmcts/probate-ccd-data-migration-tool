package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Dtspb5112SetExpiryMigrationHandlerTest {

    @Test
    void shouldMigrateLiveCaseWithMissingExpiryAndSuccessfulPaymentDate() {
        Dtspb5112PaymentService paymentService = mock();
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_RAISED);
        when(details.getData()).thenReturn(Map.of());
        when(paymentService.successfulPaymentDate(event))
            .thenReturn(Optional.of(LocalDate.of(2026, 1, 31)));

        Dtspb5112SetExpiryMigrationHandler handler = new Dtspb5112SetExpiryMigrationHandler(
            mock(), mock(), paymentService, support
        );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void shouldNotMigrateWhenExpiryAlreadyExists() {
        Dtspb5112PaymentService paymentService = mock();
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_RAISED);
        when(details.getData()).thenReturn(Map.of(Dtspb5112Constants.EXPIRY_DATE, "2026-07-31"));

        Dtspb5112SetExpiryMigrationHandler handler = new Dtspb5112SetExpiryMigrationHandler(
            mock(), mock(), paymentService, support
        );

        assertThat(handler.shouldMigrateCase(event)).isFalse();
        verify(paymentService, never()).successfulPaymentDate(event);
    }

    @Test
    void shouldSetExpiryWithoutMigrationCallbackMetadata() {
        Dtspb5112PaymentService paymentService = mock();
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        Map<String, Object> data = new HashMap<>();

        when(paymentService.successfulPaymentDate(event))
            .thenReturn(Optional.of(LocalDate.of(2026, 1, 31)));
        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any())).thenReturn(true);

        Dtspb5112SetExpiryMigrationHandler handler = new Dtspb5112SetExpiryMigrationHandler(
            mock(), mock(), paymentService, support
        );

        assertThat(handler.migrate(event)).isTrue();
        assertThat(data).containsEntry(Dtspb5112Constants.EXPIRY_DATE, "2026-07-31");
        verify(support, never()).addMigrationCallbackMetadata(any(), any());
        verify(support).submit(
            event,
            data,
            Dtspb5112SetExpiryMigrationHandler.SUMMARY,
            Dtspb5112SetExpiryMigrationHandler.DESCRIPTION
        );
    }

    @Test
    void shouldNotMigrateWhenCaseIsNotLive() {
        Dtspb5112PaymentService paymentService = mock();
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(details.getData()).thenReturn(Map.of());

        Dtspb5112SetExpiryMigrationHandler handler = new Dtspb5112SetExpiryMigrationHandler(
            mock(), mock(), paymentService, support
        );

        assertThat(handler.shouldMigrateCase(event)).isFalse();
        verify(paymentService, never()).successfulPaymentDate(event);
    }

    @Test
    void shouldNotMigrateWhenSuccessfulPaymentDoesNotExist() {
        Dtspb5112PaymentService paymentService = mock();
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_RAISED);
        when(details.getData()).thenReturn(Map.of());
        when(paymentService.successfulPaymentDate(event)).thenReturn(Optional.empty());

        Dtspb5112SetExpiryMigrationHandler handler = new Dtspb5112SetExpiryMigrationHandler(
            mock(), mock(), paymentService, support
        );

        assertThat(handler.shouldMigrateCase(event)).isFalse();
    }

    @Test
    void shouldTreatBlankExpiryAsMissing() {
        Dtspb5112PaymentService paymentService = mock();
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_RAISED);
        when(details.getData()).thenReturn(Map.of(Dtspb5112Constants.EXPIRY_DATE, ""));
        when(paymentService.successfulPaymentDate(event))
            .thenReturn(Optional.of(LocalDate.of(2026, 1, 31)));

        Dtspb5112SetExpiryMigrationHandler handler = new Dtspb5112SetExpiryMigrationHandler(
            mock(), mock(), paymentService, support
        );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenPaymentDateIsUnavailableDuringMigration() {
        Dtspb5112PaymentService paymentService = mock();
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();

        when(paymentService.successfulPaymentDate(event)).thenReturn(Optional.empty());

        Dtspb5112SetExpiryMigrationHandler handler = new Dtspb5112SetExpiryMigrationHandler(
            mock(), mock(), paymentService, support
        );

        assertThat(handler.migrate(event)).isFalse();

        verify(support, never()).mutableData(any());
        verify(support, never()).submit(any(), any(), any(), any());
    }
}
