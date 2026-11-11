package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Dtspb5112CloseExpiredMigrationHandlerTest {
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-09-14T00:00:00Z"),
        ZoneOffset.UTC
    );

    @Test
    void shouldMigrateLiveExpiredCaseRegardlessOfWhetherExpiryWasMigrated() {
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.AWAITING_WARNING_RESPONSE);
        when(details.getData()).thenReturn(Map.of(Dtspb5112Constants.EXPIRY_DATE, "2026-09-13"));

        Dtspb5112CloseExpiredMigrationHandler handler = new Dtspb5112CloseExpiredMigrationHandler(
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

        Dtspb5112CloseExpiredMigrationHandler handler = new Dtspb5112CloseExpiredMigrationHandler(
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

        Dtspb5112CloseExpiredMigrationHandler handler = new Dtspb5112CloseExpiredMigrationHandler(
            mock(), mock(), support, CLOCK
        );

        assertThat(handler.migrate(event)).isTrue();
        verify(support).addMigrationCallbackMetadata(data, Dtspb5112Constants.CLOSE_EXPIRED_ID);
    }

    @Test
    void shouldNotMigrateExpiredCaseWhenStateIsNotLive() {
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(details.getData()).thenReturn(
            Map.of(Dtspb5112Constants.EXPIRY_DATE, "2026-09-13")
        );

        Dtspb5112CloseExpiredMigrationHandler handler =
            new Dtspb5112CloseExpiredMigrationHandler(
                mock(), mock(), support, CLOCK
            );

        assertThat(handler.shouldMigrateCase(event)).isFalse();
    }

    @Test
    void shouldNotMigrateWhenExpiryDateIsMissing() {
        Dtspb5112MigrationSupport support = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();

        when(support.requireCaseDetails(event)).thenReturn(details);
        when(details.getState()).thenReturn(Dtspb5112Constants.CAVEAT_RAISED);
        when(details.getData()).thenReturn(Map.of());

        Dtspb5112CloseExpiredMigrationHandler handler =
            new Dtspb5112CloseExpiredMigrationHandler(
                mock(), mock(), support, CLOCK
            );

        assertThat(handler.shouldMigrateCase(event)).isFalse();
    }

    @Test
    void shouldQueryAllExpiredLiveCasesWithoutLastModifiedCutoff() {
        ElasticSearchHandler elasticSearchHandler = mock();
        Dtspb5112ElasticQueries queries = mock();
        UserToken userToken = mock();
        S2sToken s2sToken = mock();

        when(queries.expired(
            50,
            Dtspb5112Constants.LIVE_STATES,
            LocalDate.of(2026, 9, 14),
            Optional.empty()
        )).thenReturn(new JSONObject());

        when(elasticSearchHandler.searchCases(
            eq(Dtspb5112Constants.CLOSE_EXPIRED_ID),
            eq(userToken),
            eq(s2sToken),
            eq(CaseType.CAVEAT),
            org.mockito.ArgumentMatchers.<BiFunction<Integer, Optional<Long>, JSONObject>>any()
        )).thenAnswer(invocation -> {
            BiFunction<Integer, Optional<Long>, JSONObject> query = invocation.getArgument(4);
            query.apply(50, Optional.empty());
            return Set.of();
        });

        Dtspb5112CloseExpiredMigrationHandler handler = new Dtspb5112CloseExpiredMigrationHandler(
            elasticSearchHandler, queries, mock(), CLOCK
        );

        assertThat(handler.getCandidateCases(userToken, s2sToken)).isEmpty();
        verify(queries).expired(
            50,
            Dtspb5112Constants.LIVE_STATES,
            LocalDate.of(2026, 9, 14),
            Optional.empty()
        );
    }

}
