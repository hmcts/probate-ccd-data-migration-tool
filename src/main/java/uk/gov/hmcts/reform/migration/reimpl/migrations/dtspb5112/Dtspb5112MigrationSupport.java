package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.util.HashMap;
import java.util.Map;

@Component
public class Dtspb5112MigrationSupport {
    private final CoreCaseDataApi coreCaseDataApi;
    private final ReimplConfig commonConfig;

    public Dtspb5112MigrationSupport(CoreCaseDataApi coreCaseDataApi, ReimplConfig commonConfig) {
        this.coreCaseDataApi = coreCaseDataApi;
        this.commonConfig = commonConfig;
    }

    public MigrationEvent start(CaseSummary caseSummary, UserToken userToken, S2sToken s2sToken, String eventId) {
        if (StringUtils.isBlank(eventId)) {
            throw new IllegalStateException("No CCD event id configured for selected DTSPB-5112 migration");
        }
        UserDetails userDetails = userToken.userDetails();
        StartEventResponse response = coreCaseDataApi.startEventForCaseWorker(
            userToken.getBearerToken(),
            s2sToken.s2sToken(),
            userDetails.getId(),
            Dtspb5112Constants.JURISDICTION,
            Dtspb5112Constants.CASE_TYPE,
            caseSummary.reference().toString(),
            eventId
        );
        return new MigrationEvent(caseSummary, response, userToken, s2sToken);
    }

    public CaseDetails requireCaseDetails(MigrationEvent event) {
        CaseDetails details = event.startEventResponse().getCaseDetails();
        if (details == null || details.getData() == null) {
            throw new IllegalStateException("No case details/data returned for " + event.caseSummary().reference());
        }
        return details;
    }

    public void addMigrationCallbackMetadata(Map<String, Object> data, String migrationId) {
        JSONObject metadata = new JSONObject().put("migrationId", migrationId);
        data.put(Dtspb5112Constants.MIGRATION_CALLBACK_METADATA, metadata.toString());
    }

    public void addMigrationCallbackMetadata(Map<String, Object> data, String migrationId, String originalState) {
        JSONObject metadata = new JSONObject()
            .put("migrationId", migrationId)
            .put("originalState", originalState);

        data.put(Dtspb5112Constants.MIGRATION_CALLBACK_METADATA, metadata.toString());
    }

    public boolean submit(MigrationEvent migrationEvent, Map<String, Object> data, String summary, String description) {
        if (commonConfig.isDryRun()) {
            return true;
        }
        StartEventResponse started = migrationEvent.startEventResponse();
        CaseDetails details = requireCaseDetails(migrationEvent);
        Event event = Event.builder()
            .id(started.getEventId())
            .summary(summary)
            .description(description)
            .build();
        CaseDataContent content = CaseDataContent.builder()
            .eventToken(started.getToken())
            .event(event)
            .data(data)
            .build();
        return coreCaseDataApi.submitEventForCaseWorker(
            migrationEvent.userToken().getBearerToken(),
            migrationEvent.s2sToken().s2sToken(),
            migrationEvent.userToken().userDetails().getId(),
            details.getJurisdiction(),
            details.getCaseTypeId(),
            details.getId().toString(),
            true,
            content
        ) != null;
    }

    public Map<String, Object> mutableData(MigrationEvent event) {
        return new HashMap<>(requireCaseDetails(event).getData());
    }
}
