package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Dtspb5112CloseRollbackMigrationHandlersTest {

    @Test
    void newlyExpiredRollbackShouldRequireMatchingOriginalMigrationEvent() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();
        Dtspb5112Config config = mock();

        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(rollbackSupport.findOriginalStateForMigration(
            event,
            Dtspb5112CloseNewlyExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.CAVEAT_MATCHING)
        );

        Dtspb5112CloseNewlyExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseNewlyExpiredRollbackMigrationHandler(
                mock(),
                config,
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void existingExpiredRollbackShouldRequireMatchingOriginalMigrationEvent() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();
        Dtspb5112Config config = mock();

        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(rollbackSupport.findOriginalStateForMigration(
            event,
            Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.AWAITING_WARNING_RESPONSE)
        );

        Dtspb5112CloseExistingExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExistingExpiredRollbackMigrationHandler(
                mock(),
                config,
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void newlyExpiredRollbackShouldAddRollbackMigrationIdAndOriginalState() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();
        Map<String, Object> data = new HashMap<>();
        Dtspb5112Config config = mock();

        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(rollbackSupport.findOriginalStateForMigration(
            event,
            Dtspb5112CloseNewlyExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.CAVEAT_MATCHING)
        );
        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any()))
            .thenReturn(true);

        Dtspb5112CloseNewlyExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseNewlyExpiredRollbackMigrationHandler(
                mock(),
                config,
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
        assertThat(handler.migrate(event)).isTrue();

        verify(support).addMigrationCallbackMetadata(
            data,
            Dtspb5112Constants.S3_ROLLBACK_ID,
            Dtspb5112Constants.CAVEAT_MATCHING
        );

        verify(rollbackSupport, times(1)).findOriginalStateForMigration(
            event,
            Dtspb5112CloseNewlyExpiredMigrationHandler.DESCRIPTION
        );
    }

    @Test
    void existingExpiredRollbackShouldAddRollbackMigrationIdAndOriginalState() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();
        Map<String, Object> data = new HashMap<>();
        Dtspb5112Config config = mock();

        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(rollbackSupport.findOriginalStateForMigration(
            event,
            Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.AWAITING_WARNING_RESPONSE)
        );
        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any()))
            .thenReturn(true);

        Dtspb5112CloseExistingExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExistingExpiredRollbackMigrationHandler(
                mock(),
                config,
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
        assertThat(handler.migrate(event)).isTrue();

        verify(support).addMigrationCallbackMetadata(
            data,
            Dtspb5112Constants.S4_ROLLBACK_ID,
            Dtspb5112Constants.AWAITING_WARNING_RESPONSE
        );

        verify(rollbackSupport, times(1)).findOriginalStateForMigration(
            event,
            Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION
        );
    }

    @Test
    void closeRollbackShouldNotMigrateWhenCaseIsNotClosed() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_RAISED);

        Dtspb5112CloseExistingExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExistingExpiredRollbackMigrationHandler(
                mock(),
                mock(),
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.shouldMigrateCase(event)).isFalse();

        verify(rollbackSupport, never())
            .findOriginalStateForMigration(any(), any());
    }

    @Test
    void closeRollbackShouldNotMigrateWithoutOriginalMigrationEvent() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(rollbackSupport.findOriginalStateForMigration(
            event,
            Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(Optional.empty());

        Dtspb5112CloseExistingExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExistingExpiredRollbackMigrationHandler(
                mock(),
                mock(),
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.shouldMigrateCase(event)).isFalse();
    }

    @Test
    void closeRollbackShouldNotMigrateWhenOriginalStateIsNotLive() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(rollbackSupport.findOriginalStateForMigration(
            event,
            Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.PA_APP_CREATED)
        );

        Dtspb5112CloseExistingExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExistingExpiredRollbackMigrationHandler(
                mock(),
                mock(),
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.shouldMigrateCase(event)).isFalse();
    }

    @Test
    void closeRollbackShouldResolveOriginalStateWhenMigrateIsCalledWithoutShouldMigrate() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        Map<String, Object> data = new HashMap<>();

        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(rollbackSupport.findOriginalStateForMigration(
            event,
            Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.CAVEAT_MATCHING)
        );
        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any()))
            .thenReturn(true);

        Dtspb5112CloseExistingExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExistingExpiredRollbackMigrationHandler(
                mock(),
                mock(),
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.migrate(event)).isTrue();

        verify(support).addMigrationCallbackMetadata(
            data,
            Dtspb5112Constants.S4_ROLLBACK_ID,
            Dtspb5112Constants.CAVEAT_MATCHING
        );

        verify(rollbackSupport).findOriginalStateForMigration(
            event,
            Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION
        );
    }
}
