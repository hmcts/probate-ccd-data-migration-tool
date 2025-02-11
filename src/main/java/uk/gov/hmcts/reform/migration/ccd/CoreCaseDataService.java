package uk.gov.hmcts.reform.migration.ccd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.migration.auth.AuthUtil;
import uk.gov.hmcts.reform.migration.service.DataMigrationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class CoreCaseDataService {

    private static final String DELETED_STATE = "Deleted";

    @Autowired
    private IdamClient idamClient;
    @Autowired
    private AuthTokenGenerator authTokenGenerator;
    @Autowired
    private CoreCaseDataApi coreCaseDataApi;

    @Autowired
    private DataMigrationService<Map<String, Object>> dataMigrationService;

    public CaseDetails update(String authorisation, String eventId,
                              String eventSummary,
                              String eventDescription,
                              String caseType,
                              CaseDetails caseDetails) {
        String caseId = String.valueOf(caseDetails.getId());
        UserDetails userDetails = idamClient.getUserDetails(AuthUtil.getBearerToken(authorisation));

        StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
            AuthUtil.getBearerToken(authorisation),
            authTokenGenerator.generate(),
            userDetails.getId(),
            caseDetails.getJurisdiction(),
            caseType,
            caseId,
            eventId);

        CaseDetails updatedCaseDetails = startEventResponse.getCaseDetails();

        if (isInactiveCase(updatedCaseDetails)) {
            CaseDataContent caseDataContent = CaseDataContent.builder()
                .eventToken(startEventResponse.getToken())
                .event(
                    Event.builder()
                        .id(startEventResponse.getEventId())
                        .summary(eventSummary)
                        .description(eventDescription)
                        .build()
                ).data(dataMigrationService.migrate(updatedCaseDetails)).build();
            return coreCaseDataApi.submitEventForCaseWorker(
                AuthUtil.getBearerToken(authorisation),
                authTokenGenerator.generate(),
                userDetails.getId(),
                updatedCaseDetails.getJurisdiction(),
                caseType,
                caseId,
                true,
                caseDataContent);
        } else {
            return null;
        }
    }

    public CaseDetails rollback(String authorisation, String eventId,
                                String eventSummary,
                                String eventDescription,
                                String caseType,
                                CaseDetails caseDetails) {
        String caseId = String.valueOf(caseDetails.getId());
        UserDetails userDetails = idamClient.getUserDetails(AuthUtil.getBearerToken(authorisation));

        StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
            AuthUtil.getBearerToken(authorisation),
            authTokenGenerator.generate(),
            userDetails.getId(),
            caseDetails.getJurisdiction(),
            caseType,
            caseId,
            eventId);

        CaseDetails updatedCaseDetails = startEventResponse.getCaseDetails();

        if (isInactiveCase(updatedCaseDetails)) {
            CaseDataContent caseDataContent = CaseDataContent.builder()
                .eventToken(startEventResponse.getToken())
                .event(
                    Event.builder()
                        .id(startEventResponse.getEventId())
                        .summary(eventSummary)
                        .description(eventDescription)
                        .build()
                ).data(dataMigrationService.rollback(updatedCaseDetails.getData()))
                .build();
            return coreCaseDataApi.submitEventForCaseWorker(
                AuthUtil.getBearerToken(authorisation),
                authTokenGenerator.generate(),
                userDetails.getId(),
                updatedCaseDetails.getJurisdiction(),
                caseType,
                caseId,
                true,
                caseDataContent);
        } else {
            return null;
        }
    }

    private boolean isInactiveCase(CaseDetails updatedCaseDetails) {
        if (DELETED_STATE.equalsIgnoreCase(updatedCaseDetails.getState())) {
            log.info("Case ID: {}, State: {}", updatedCaseDetails.getId(), updatedCaseDetails.getState());
            return true;
        }
        LocalDateTime lastModified = updatedCaseDetails.getLastModified();
        if (lastModified == null) {
            log.info("Case ID: {}, Last Modified: {}", updatedCaseDetails.getId(), lastModified);
            return false;
        }

        LocalDate cutOffDate = LocalDate.now().minusDays(180);
        boolean isInactive = lastModified.isBefore(cutOffDate.atStartOfDay());

        log.info("Case ID: {}, Last Modified: {}, Cut-Off Date: {}, Is Inactive: {}",
            updatedCaseDetails.getId(), lastModified, cutOffDate, isInactive);

        return isInactive;
    }
}
