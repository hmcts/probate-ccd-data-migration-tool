package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Dtspb5112CloseExistingExpiredMigrationHandlerTest {
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-09-14T00:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void shouldMigrateAnyLiveCaseWithExpiredExpiryDate() {
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.AWAITING_WARNING_RESPONSE);
        when(details.getData()).thenReturn(Map.of(Dtspb5112Constants.EXPIRY_DATE, "2026-09-13"));

        Dtspb5112CloseExistingExpiredMigrationHandler handler = new Dtspb5112CloseExistingExpiredMigrationHandler(
            mock(), mock(), support, CLOCK
        );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void shouldNotMigrateLiveCaseWhenExpiryDateHasNotExpired() {
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.AWAITING_WARNING_RESPONSE);
        when(details.getData()).thenReturn(Map.of(Dtspb5112Constants.EXPIRY_DATE, "2026-09-14"));

        Dtspb5112CloseExistingExpiredMigrationHandler handler = new Dtspb5112CloseExistingExpiredMigrationHandler(
            mock(), mock(), support, CLOCK
        );

        assertThat(handler.shouldMigrateCase(event)).isFalse();
    }

    @Test
    void shouldAddMigrationIdWhenClosingCase() {
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        Map<String, Object> data = new HashMap<>();

        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any())).thenReturn(true);

        Dtspb5112CloseExistingExpiredMigrationHandler handler = new Dtspb5112CloseExistingExpiredMigrationHandler(
            mock(), mock(), support, CLOCK
        );

        assertThat(handler.migrate(event)).isTrue();
        verify(support).addMigrationCallbackMetadata(data, Dtspb5112Constants.S4_ID);
    }
}
