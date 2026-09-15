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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Dtspb5112SetExpiryRollbackMigrationHandlerTest {

    @Test
    void shouldMigrateWhenExpiryMatchesSuccessfulPaymentAndMigrationEventExists() {
        Dtspb5112PaymentService paymentService = mock();
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(paymentService.successfulPaymentDate(event))
            .thenReturn(Optional.of(LocalDate.of(2026, 1, 1)));
        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getData()).thenReturn(Map.of(Dtspb5112Constants.EXPIRY_DATE, "2026-07-01"));
        when(rollbackSupport.hasMigrationEvent(event, Dtspb5112SetExpiryMigrationHandler.DESCRIPTION))
            .thenReturn(true);

        Dtspb5112SetExpiryRollbackMigrationHandler handler = new Dtspb5112SetExpiryRollbackMigrationHandler(
            mock(), mock(), paymentService, support, rollbackSupport
        );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void shouldNotMigrateWhenOriginalMigrationEventDoesNotExist() {
        Dtspb5112PaymentService paymentService = mock();
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(paymentService.successfulPaymentDate(event))
            .thenReturn(Optional.of(LocalDate.of(2026, 1, 1)));
        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getData()).thenReturn(Map.of(Dtspb5112Constants.EXPIRY_DATE, "2026-07-01"));
        when(rollbackSupport.hasMigrationEvent(event, Dtspb5112SetExpiryMigrationHandler.DESCRIPTION))
            .thenReturn(false);

        Dtspb5112SetExpiryRollbackMigrationHandler handler = new Dtspb5112SetExpiryRollbackMigrationHandler(
            mock(), mock(), paymentService, support, rollbackSupport
        );

        assertThat(handler.shouldMigrateCase(event)).isFalse();
    }

    @Test
    void shouldSetExpiryToNullAndAddRollbackMigrationId() {
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        Map<String, Object> data = new HashMap<>();
        data.put(Dtspb5112Constants.EXPIRY_DATE, "2026-07-01");

        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any())).thenReturn(true);

        Dtspb5112SetExpiryRollbackMigrationHandler handler = new Dtspb5112SetExpiryRollbackMigrationHandler(
            mock(), mock(), mock(), support, mock()
        );

        assertThat(handler.migrate(event)).isTrue();
        assertThat(data).containsEntry(Dtspb5112Constants.EXPIRY_DATE, null);
        verify(support).addMigrationCallbackMetadata(data, Dtspb5112Constants.S2_ROLLBACK_ID);
        verify(support).submit(
            event,
            data,
            Dtspb5112SetExpiryRollbackMigrationHandler.SUMMARY,
            Dtspb5112SetExpiryRollbackMigrationHandler.DESCRIPTION
        );
    }
}
