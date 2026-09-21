package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.CaseEventsApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.CaseEventDetail;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class Dtspb5112RollbackSupport {

    private final CaseEventsApi caseEventsApi;
    private final Dtspb5112MigrationSupport migrationSupport;

    public Dtspb5112RollbackSupport(
        CaseEventsApi caseEventsApi,
        Dtspb5112MigrationSupport migrationSupport
    ) {
        this.caseEventsApi = caseEventsApi;
        this.migrationSupport = migrationSupport;
    }

    public boolean hasMigrationEvent(
        MigrationEvent event,
        String description
    ) {
        return getCaseEvents(event).stream()
            .anyMatch(caseEvent ->
                Dtspb5112Constants.MIGRATION_EVENT.equals(caseEvent.getId())
                    && description.equals(caseEvent.getDescription())
            );
    }

    public Optional<String> findOriginalStateForMigration(
        MigrationEvent event,
        String description
    ) {
        List<CaseEventDetail> events = getCaseEvents(event).stream()
            .sorted(Comparator.comparing(CaseEventDetail::getCreatedDate))
            .toList();

        for (int i = events.size() - 1; i >= 0; i--) {
            CaseEventDetail current = events.get(i);

            if (Dtspb5112Constants.MIGRATION_EVENT.equals(current.getId())
                && description.equals(current.getDescription())) {

                if (i == 0) {
                    return Optional.empty();
                }

                return Optional.ofNullable(events.get(i - 1).getStateId());
            }
        }

        return Optional.empty();
    }

    private List<CaseEventDetail> getCaseEvents(MigrationEvent event) {
        CaseDetails details = migrationSupport.requireCaseDetails(event);

        return caseEventsApi.findEventDetailsForCase(
            event.userToken().getBearerToken(),
            event.s2sToken().s2sToken(),
            event.userToken().userDetails().getId(),
            details.getJurisdiction(),
            details.getCaseTypeId(),
            details.getId().toString()
        );
    }
}
