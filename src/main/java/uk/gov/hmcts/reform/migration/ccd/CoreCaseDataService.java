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

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CoreCaseDataService {

    private static final String STOP_REASON_LIST = "boCaseStopReasonList";
    private static final String CAVEAT_MATCH = "CaveatMatch";
    private static final String PERMANENT_CAVEAT = "Permanent Caveat";
    private static final String CASE_STOP_REASON = "caseStopReason";

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

        if (isCaveatMatchOrPermanentCaveat(updatedCaseDetails.getData().get(STOP_REASON_LIST))) {
            CaseDataContent caseDataContent = CaseDataContent.builder()
                .eventToken(startEventResponse.getToken())
                .event(
                    Event.builder()
                        .id(startEventResponse.getEventId())
                        .summary(eventSummary)
                        .description(eventDescription)
                        .build()
                ).data(dataMigrationService.migrate(updatedCaseDetails.getData())).build();
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

        if (isCaveatMatchOrPermanentCaveat(updatedCaseDetails.getData().get(STOP_REASON_LIST))) {
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

    @SuppressWarnings("unchecked")
    private boolean isCaveatMatchOrPermanentCaveat(Object stopReasonListObj) {
        if (stopReasonListObj instanceof List) {
            List<Map<String, Object>> boCaseStopReasonList =
                (List<Map<String, Object>>) stopReasonListObj;

            for (Map<String, Object> reasonEntry : boCaseStopReasonList) {
                Map<String, Object> value = (Map<String, Object>) reasonEntry.get("value");
                String caseStopReason = (String) value.get(CASE_STOP_REASON);

                if (CAVEAT_MATCH.equals(caseStopReason) || PERMANENT_CAVEAT.equals(caseStopReason)) {
                    return true;
                }
            }
        }

        return false;
    }
}
