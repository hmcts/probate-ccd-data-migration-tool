package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Dtspb5112MigrationSupportTest {

    @Test
    void shouldAddMigrationIdAsCallbackMetadata() {
        Dtspb5112MigrationSupport support = new Dtspb5112MigrationSupport(mock(), mock());
        Map<String, Object> data = new HashMap<>();

        support.addMigrationCallbackMetadata(data, Dtspb5112Constants.S3_ID);

        JSONObject metadata = new JSONObject(
            data.get(Dtspb5112Constants.MIGRATION_CALLBACK_METADATA).toString()
        );
        assertThat(metadata.getString("migrationId")).isEqualTo(Dtspb5112Constants.S3_ID);
    }

    @Test
    void shouldCopyCaseDataIntoMutableMap() {
        Dtspb5112MigrationSupport support =
            new Dtspb5112MigrationSupport(mock(), mock());

        MigrationEvent event = mock();
        StartEventResponse response = mock();
        CaseDetails details = mock();

        Map<String, Object> original = Map.of(
            "expiryDate", "2026-07-31"
        );

        when(event.startEventResponse()).thenReturn(response);
        when(response.getCaseDetails()).thenReturn(details);
        when(details.getData()).thenReturn(original);

        Map<String, Object> result = support.mutableData(event);

        assertThat(result)
            .containsExactlyEntriesOf(original)
            .isNotSameAs(original);
    }

    @Test
    void shouldThrowWhenCaseDetailsAreMissing() {
        Dtspb5112MigrationSupport support =
            new Dtspb5112MigrationSupport(mock(), mock());

        MigrationEvent event = mock();
        StartEventResponse response = mock();

        when(event.startEventResponse()).thenReturn(response);
        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(response.getCaseDetails()).thenReturn(null);

        assertThatThrownBy(() -> support.requireCaseDetails(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No case details/data returned");
    }

    @Test
    void shouldThrowWhenCaseDataAreMissing() {
        Dtspb5112MigrationSupport support =
            new Dtspb5112MigrationSupport(mock(), mock());

        MigrationEvent event = mock();
        StartEventResponse response = mock();
        CaseDetails details = mock();

        when(event.startEventResponse()).thenReturn(response);
        when(event.caseSummary()).thenReturn(
            new CaseSummary(123L, CaseType.CAVEAT)
        );
        when(response.getCaseDetails()).thenReturn(details);
        when(details.getData()).thenReturn(null);

        assertThatThrownBy(() -> support.requireCaseDetails(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("No case details/data returned for 123");
    }

    @Test
    void shouldRejectBlankEventId() {
        Dtspb5112MigrationSupport support =
            new Dtspb5112MigrationSupport(mock(), mock());

        assertThatThrownBy(() ->
            support.start(mock(), mock(), mock(), " ")
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "No CCD event id configured for selected DTSPB-5112 migration"
            );
    }

    @Test
    void shouldReturnTrueWithoutSubmittingWhenDryRun() {
        CoreCaseDataApi coreCaseDataApi = mock();
        ReimplConfig config = mock();

        when(config.isDryRun()).thenReturn(true);

        Dtspb5112MigrationSupport support =
            new Dtspb5112MigrationSupport(coreCaseDataApi, config);

        boolean result = support.submit(
            mock(),
            new HashMap<>(),
            "summary",
            "description"
        );

        assertThat(result).isTrue();

        verifyNoInteractions(coreCaseDataApi);
    }

    @Test
    void shouldSubmitMigrationEvent() {
        MigrationEvent migrationEvent = mock();
        StartEventResponse started = mock();

        Map<String, Object> data = new HashMap<>();
        data.put("expiryDate", "2026-07-31");

        ReimplConfig config = mock();
        when(config.isDryRun()).thenReturn(false);

        S2sToken s2sToken = mock();
        UserToken userToken = mock();
        when(migrationEvent.startEventResponse()).thenReturn(started);
        when(migrationEvent.userToken()).thenReturn(userToken);
        when(migrationEvent.s2sToken()).thenReturn(s2sToken);

        CaseDetails details = mock();
        when(started.getEventId()).thenReturn("boHistoryCorrection");
        when(started.getToken()).thenReturn("event-token");
        when(started.getCaseDetails()).thenReturn(details);

        when(details.getData()).thenReturn(data);
        when(details.getJurisdiction()).thenReturn("PROBATE");
        when(details.getCaseTypeId()).thenReturn("Caveat");
        when(details.getId()).thenReturn(123L);

        UserDetails userDetails = mock();
        when(userToken.getBearerToken()).thenReturn("user-token");
        when(userToken.userDetails()).thenReturn(userDetails);
        when(userDetails.getId()).thenReturn("user-id");

        when(s2sToken.s2sToken()).thenReturn("service-token");

        CoreCaseDataApi coreCaseDataApi = mock();
        when(coreCaseDataApi.submitEventForCaseWorker(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any()
        )).thenReturn(details);

        Dtspb5112MigrationSupport support =
            new Dtspb5112MigrationSupport(coreCaseDataApi, config);
        boolean result = support.submit(
            migrationEvent,
            data,
            "DTSPB-5112 summary",
            "DTSPB-5112 description"
        );

        assertThat(result).isTrue();

        ArgumentCaptor<CaseDataContent> contentCaptor =
            ArgumentCaptor.forClass(CaseDataContent.class);

        verify(coreCaseDataApi).submitEventForCaseWorker(
            eq("user-token"),
            eq("service-token"),
            eq("user-id"),
            eq("PROBATE"),
            eq("Caveat"),
            eq("123"),
            eq(true),
            contentCaptor.capture()
        );

        CaseDataContent content = contentCaptor.getValue();

        assertThat(content.getEventToken()).isEqualTo("event-token");
        assertThat(content.getData()).isSameAs(data);
        assertThat(content.getEvent().getId()).isEqualTo("boHistoryCorrection");
        assertThat(content.getEvent().getSummary()).isEqualTo("DTSPB-5112 summary");
        assertThat(content.getEvent().getDescription()).isEqualTo("DTSPB-5112 description");
    }

    @Test
    void shouldAddMigrationIdAndOriginalStateAsCallbackMetadata() {
        Dtspb5112MigrationSupport support =
            new Dtspb5112MigrationSupport(mock(), mock());

        Map<String, Object> data = new HashMap<>();

        support.addMigrationCallbackMetadata(
            data,
            Dtspb5112Constants.S4_ROLLBACK_ID,
            Dtspb5112Constants.AWAITING_WARNING_RESPONSE
        );

        JSONObject metadata = new JSONObject(
            data.get(
                Dtspb5112Constants.MIGRATION_CALLBACK_METADATA
            ).toString()
        );

        assertThat(metadata.getString("migrationId"))
            .isEqualTo(Dtspb5112Constants.S4_ROLLBACK_ID);

        assertThat(metadata.getString("originalState"))
            .isEqualTo(Dtspb5112Constants.AWAITING_WARNING_RESPONSE);
    }
}
