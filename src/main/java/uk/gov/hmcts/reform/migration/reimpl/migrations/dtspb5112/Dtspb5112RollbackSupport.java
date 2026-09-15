package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CaseEventsApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.CaseEventDetail;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;

import java.util.List;

@Component
public class Dtspb5112RollbackSupport {
    private final CaseEventsApi caseEventsApi;
    private final Dtspb5112MigrationSupport migrationSupport;

    public Dtspb5112RollbackSupport(CaseEventsApi caseEventsApi, Dtspb5112MigrationSupport migrationSupport) {
        this.caseEventsApi = caseEventsApi;
        this.migrationSupport = migrationSupport;
    }

    public boolean hasMigrationEvent(MigrationEvent event, String description) {
        CaseDetails details = migrationSupport.requireCaseDetails(event);
        List<CaseEventDetail> events = caseEventsApi.findEventDetailsForCase(
            event.userToken().getBearerToken(),
            event.s2sToken().s2sToken(),
            event.userToken().userDetails().getId(),
            details.getJurisdiction(),
            details.getCaseTypeId(),
            details.getId().toString()
        );
        return events.stream().anyMatch(caseEvent -> isMigrationEvent(caseEvent, description));
    }

    private boolean isMigrationEvent(CaseEventDetail caseEvent, String description) {
        return Dtspb5112Constants.MIGRATION_EVENT.equals(caseEvent.getId())
            && description.equals(caseEvent.getDescription());
    }
}
