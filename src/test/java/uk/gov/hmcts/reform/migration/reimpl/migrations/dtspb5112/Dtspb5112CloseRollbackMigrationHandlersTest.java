package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Dtspb5112CloseRollbackMigrationHandlersTest {

    @Test
    void newlyExpiredRollbackShouldRequireMatchingOriginalMigrationEvent() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();
        Dtspb5112Config config = mock(Dtspb5112Config.class);

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(rollbackSupport.hasMigrationEvent(event, Dtspb5112CloseNewlyExpiredMigrationHandler.DESCRIPTION))
            .thenReturn(true);

        Dtspb5112CloseNewlyExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseNewlyExpiredRollbackMigrationHandler(mock(), config, mock(), support, rollbackSupport);

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void existingExpiredRollbackShouldRequireMatchingOriginalMigrationEvent() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();
        Dtspb5112Config config = mock(Dtspb5112Config.class);

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(rollbackSupport.hasMigrationEvent(event, Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION))
            .thenReturn(true);

        Dtspb5112CloseExistingExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExistingExpiredRollbackMigrationHandler(mock(), config, mock(), support, rollbackSupport);

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void newlyExpiredRollbackShouldAddRollbackMigrationId() {
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        Map<String, Object> data = new HashMap<>();
        Dtspb5112Config config = mock(Dtspb5112Config.class);

        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any())).thenReturn(true);

        Dtspb5112CloseNewlyExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseNewlyExpiredRollbackMigrationHandler(mock(), config, mock(), support, mock());

        assertThat(handler.migrate(event)).isTrue();
        verify(support).addMigrationCallbackMetadata(data, Dtspb5112Constants.S3_ROLLBACK_ID);
    }

    @Test
    void existingExpiredRollbackShouldAddRollbackMigrationId() {
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        Map<String, Object> data = new HashMap<>();
        Dtspb5112Config config = mock(Dtspb5112Config.class);

        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any())).thenReturn(true);

        Dtspb5112CloseExistingExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExistingExpiredRollbackMigrationHandler(mock(), config, mock(), support, mock());

        assertThat(handler.migrate(event)).isTrue();
        verify(support).addMigrationCallbackMetadata(data, Dtspb5112Constants.S4_ROLLBACK_ID);
    }
}
