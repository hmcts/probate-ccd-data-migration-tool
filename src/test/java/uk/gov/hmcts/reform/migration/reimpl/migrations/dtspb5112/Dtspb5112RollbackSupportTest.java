package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.CaseEventsApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.CaseEventDetail;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Dtspb5112RollbackSupportTest {

    @Test
    void shouldRequireHistoryCorrectionEventIdAndMatchingDescription() {
        CaseEventsApi caseEventsApi = mock();
        Dtspb5112MigrationSupport migrationSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();
        UserToken userToken = mock();
        S2sToken s2sToken = mock();
        UserDetails userDetails = mock();
        CaseEventDetail wrongEventId = mock();
        CaseEventDetail matchingEvent = mock();

        when(migrationSupport.requireCaseDetails(event)).thenReturn(details);
        when(details.getJurisdiction()).thenReturn(Dtspb5112Constants.JURISDICTION);
        when(details.getCaseTypeId()).thenReturn(Dtspb5112Constants.CASE_TYPE);
        when(details.getId()).thenReturn(123L);
        when(event.userToken()).thenReturn(userToken);
        when(event.s2sToken()).thenReturn(s2sToken);
        when(userToken.getBearerToken()).thenReturn("user-token");
        when(userToken.userDetails()).thenReturn(userDetails);
        when(userDetails.getId()).thenReturn("user-id");
        when(s2sToken.s2sToken()).thenReturn("s2s-token");

        when(wrongEventId.getId()).thenReturn(Dtspb5112Constants.ROLLBACK_EVENT);
        when(wrongEventId.getDescription()).thenReturn(Dtspb5112SetExpiryMigrationHandler.DESCRIPTION);
        when(matchingEvent.getId()).thenReturn(Dtspb5112Constants.MIGRATION_EVENT);
        when(matchingEvent.getDescription()).thenReturn(Dtspb5112SetExpiryMigrationHandler.DESCRIPTION);

        when(caseEventsApi.findEventDetailsForCase(
            "user-token",
            "s2s-token",
            "user-id",
            Dtspb5112Constants.JURISDICTION,
            Dtspb5112Constants.CASE_TYPE,
            "123"
        )).thenReturn(List.of(wrongEventId, matchingEvent));

        Dtspb5112RollbackSupport rollbackSupport = new Dtspb5112RollbackSupport(
            caseEventsApi,
            migrationSupport
        );

        assertThat(rollbackSupport.hasMigrationEvent(
            event,
            Dtspb5112SetExpiryMigrationHandler.DESCRIPTION
        )).isTrue();
    }

    @Test
    void shouldNotMatchRollbackEventWithSameDescription() {
        CaseEventsApi caseEventsApi = mock();
        Dtspb5112MigrationSupport migrationSupport = mock();
        MigrationEvent event = mock();
        CaseDetails details = mock();
        UserToken userToken = mock();
        S2sToken s2sToken = mock();
        UserDetails userDetails = mock();
        CaseEventDetail rollbackEvent = mock();

        when(migrationSupport.requireCaseDetails(event)).thenReturn(details);
        when(details.getJurisdiction()).thenReturn(Dtspb5112Constants.JURISDICTION);
        when(details.getCaseTypeId()).thenReturn(Dtspb5112Constants.CASE_TYPE);
        when(details.getId()).thenReturn(123L);
        when(event.userToken()).thenReturn(userToken);
        when(event.s2sToken()).thenReturn(s2sToken);
        when(userToken.getBearerToken()).thenReturn("user-token");
        when(userToken.userDetails()).thenReturn(userDetails);
        when(userDetails.getId()).thenReturn("user-id");
        when(s2sToken.s2sToken()).thenReturn("s2s-token");

        when(rollbackEvent.getId()).thenReturn(Dtspb5112Constants.ROLLBACK_EVENT);
        when(rollbackEvent.getDescription()).thenReturn(Dtspb5112SetExpiryMigrationHandler.DESCRIPTION);

        when(caseEventsApi.findEventDetailsForCase(
            "user-token",
            "s2s-token",
            "user-id",
            Dtspb5112Constants.JURISDICTION,
            Dtspb5112Constants.CASE_TYPE,
            "123"
        )).thenReturn(List.of(rollbackEvent));

        Dtspb5112RollbackSupport rollbackSupport = new Dtspb5112RollbackSupport(
            caseEventsApi,
            migrationSupport
        );

        assertThat(rollbackSupport.hasMigrationEvent(
            event,
            Dtspb5112SetExpiryMigrationHandler.DESCRIPTION
        )).isFalse();
    }

    @Test
    void shouldFindOriginalStateBeforeMigrationEvent() {
        CaseEventsApi caseEventsApi = mock();
        Dtspb5112MigrationSupport migrationSupport = mock();

        MigrationEvent event = mock();
        CaseDetails details = mock();
        UserToken userToken = mock();
        S2sToken s2sToken = mock();
        UserDetails userDetails = mock();

        when(migrationSupport.requireCaseDetails(event)).thenReturn(details);

        when(event.userToken()).thenReturn(userToken);
        when(event.s2sToken()).thenReturn(s2sToken);

        when(userToken.getBearerToken()).thenReturn("user-token");
        when(userToken.userDetails()).thenReturn(userDetails);
        when(userDetails.getId()).thenReturn("user-id");

        when(s2sToken.s2sToken()).thenReturn("s2s-token");

        when(details.getJurisdiction()).thenReturn("PROBATE");
        when(details.getCaseTypeId()).thenReturn("Caveat");
        when(details.getId()).thenReturn(123L);

        CaseEventDetail previousEvent = mock();
        when(previousEvent.getId()).thenReturn("someEvent");
        when(previousEvent.getStateId())
            .thenReturn(Dtspb5112Constants.AWAITING_WARNING_RESPONSE);
        when(previousEvent.getCreatedDate())
            .thenReturn(LocalDateTime.of(2026, 9, 10, 10, 0));

        CaseEventDetail migrationEvent = mock();
        when(migrationEvent.getId())
            .thenReturn(Dtspb5112Constants.MIGRATION_EVENT);
        when(migrationEvent.getDescription())
            .thenReturn(Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION);
        when(migrationEvent.getStateId())
            .thenReturn(Dtspb5112Constants.CAVEAT_CLOSED);
        when(migrationEvent.getCreatedDate())
            .thenReturn(LocalDateTime.of(2026, 9, 10, 11, 0));

        when(caseEventsApi.findEventDetailsForCase(
            "user-token",
            "s2s-token",
            "user-id",
            "PROBATE",
            "Caveat",
            "123"
        )).thenReturn(List.of(
            migrationEvent,
            previousEvent
        ));

        Dtspb5112RollbackSupport rollbackSupport =
            new Dtspb5112RollbackSupport(
                caseEventsApi,
                migrationSupport
            );

        assertThat(
            rollbackSupport.findOriginalStateForMigration(
                event,
                Dtspb5112CloseExistingExpiredMigrationHandler.DESCRIPTION
            )
        ).contains(Dtspb5112Constants.AWAITING_WARNING_RESPONSE);
    }
}
