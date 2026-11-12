package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Dtspb5112CloseExpiredRollbackMigrationHandlerTest {

    @Test
    void shouldRollbackClosedCaseToCaveatMatching() {
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
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.CAVEAT_MATCHING)
        );

        Dtspb5112CloseExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExpiredRollbackMigrationHandler(
                mock(),
                config,
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void shouldRollbackClosedCaseToAwaitingWarningResponse() {
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
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.AWAITING_WARNING_RESPONSE)
        );

        Dtspb5112CloseExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExpiredRollbackMigrationHandler(
                mock(),
                config,
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.shouldMigrateCase(event)).isTrue();
    }

    @Test
    void shouldSendRollbackIdAndCaveatMatchingStateUsingCachedHistory() {
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
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.CAVEAT_MATCHING)
        );
        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any()))
            .thenReturn(true);

        Dtspb5112CloseExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExpiredRollbackMigrationHandler(
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
            Dtspb5112Constants.CLOSE_EXPIRED_ROLLBACK_ID,
            Dtspb5112Constants.CAVEAT_MATCHING
        );

        verify(rollbackSupport, times(1)).findOriginalStateForMigration(
            event,
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        );
    }

    @Test
    void shouldSendRollbackIdAndAwaitingWarningResponseStateUsingCachedHistory() {
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
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.AWAITING_WARNING_RESPONSE)
        );
        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any()))
            .thenReturn(true);

        Dtspb5112CloseExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExpiredRollbackMigrationHandler(
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
            Dtspb5112Constants.CLOSE_EXPIRED_ROLLBACK_ID,
            Dtspb5112Constants.AWAITING_WARNING_RESPONSE
        );

        verify(rollbackSupport, times(1)).findOriginalStateForMigration(
            event,
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        );
    }

    @Test
    void closeRollbackShouldNotMigrateWhenCaseIsNotClosed() {
        Dtspb5112MigrationSupport support = mock();
        Dtspb5112RollbackSupport rollbackSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_RAISED);

        Dtspb5112CloseExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExpiredRollbackMigrationHandler(
                mock(), mock(), mock(), support, rollbackSupport
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
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(Optional.empty());

        Dtspb5112CloseExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExpiredRollbackMigrationHandler(
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
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.PA_APP_CREATED)
        );

        Dtspb5112CloseExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExpiredRollbackMigrationHandler(
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
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        )).thenReturn(
            Optional.of(Dtspb5112Constants.CAVEAT_MATCHING)
        );
        when(support.mutableData(event)).thenReturn(data);
        when(support.submit(eq(event), eq(data), any(), any()))
            .thenReturn(true);

        Dtspb5112CloseExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExpiredRollbackMigrationHandler(
                mock(),
                mock(),
                mock(),
                support,
                rollbackSupport
            );

        assertThat(handler.migrate(event)).isTrue();

        verify(support).addMigrationCallbackMetadata(
            data,
            Dtspb5112Constants.CLOSE_EXPIRED_ROLLBACK_ID,
            Dtspb5112Constants.CAVEAT_MATCHING
        );

        verify(rollbackSupport).findOriginalStateForMigration(
            event,
            Dtspb5112CloseExpiredMigrationHandler.DESCRIPTION
        );
    }

    @Test
    void rollbackShouldQueryClosedCasesModifiedSinceRollbackDate() {
        ElasticSearchHandler elasticSearchHandler = mock();
        Dtspb5112Config config = mock();
        Dtspb5112ElasticQueries queries = mock();
        UserToken userToken = mock();
        S2sToken s2sToken = mock();

        when(config.getRollbackDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(queries.stateOnlyModifiedSince(
            50,
            Set.of(Dtspb5112Constants.CAVEAT_CLOSED),
            LocalDate.of(2026, 9, 1),
            Optional.empty()
        )).thenReturn(new JSONObject());

        when(elasticSearchHandler.searchCases(
            eq(Dtspb5112Constants.CLOSE_EXPIRED_ROLLBACK_ID),
            eq(userToken),
            eq(s2sToken),
            eq(CaseType.CAVEAT),
            org.mockito.ArgumentMatchers.<BiFunction<Integer, Optional<Long>, JSONObject>>any()
        )).thenAnswer(invocation -> {
            BiFunction<Integer, Optional<Long>, JSONObject> query = invocation.getArgument(4);
            query.apply(50, Optional.empty());
            return Set.of();
        });

        Dtspb5112CloseExpiredRollbackMigrationHandler handler =
            new Dtspb5112CloseExpiredRollbackMigrationHandler(
                elasticSearchHandler, config, queries, mock(), mock()
            );

        assertThat(handler.getCandidateCases(userToken, s2sToken)).isEmpty();
        verify(queries).stateOnlyModifiedSince(
            50,
            Set.of(Dtspb5112Constants.CAVEAT_CLOSED),
            LocalDate.of(2026, 9, 1),
            Optional.empty()
        );
    }

}
