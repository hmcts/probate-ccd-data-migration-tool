package uk.gov.hmcts.reform.migration.ccd;

import lombok.extern.slf4j.Slf4j;
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

import java.util.Map;

@Slf4j
@Service
public class CoreCaseDataService {

    private final IdamClient idamClient;
    private final AuthTokenGenerator authTokenGenerator;
    private final CoreCaseDataApi coreCaseDataApi;

    private static final String AUTO_CLOSED_EXPIRY = "autoClosedExpiry";
    private static final String YES = "Yes";

    public CoreCaseDataService(
            final IdamClient idamClient,
            final AuthTokenGenerator authTokenGenerator,
            final CoreCaseDataApi coreCaseDataApi) {
        this.idamClient = idamClient;
        this.authTokenGenerator = authTokenGenerator;
        this.coreCaseDataApi = coreCaseDataApi;
    }

    public CaseDetails update(
            String authorisation,
            String eventId,
            String eventSummary,
            String eventDescription,
            String caseType,
            CaseDetails caseDetails) {
        log.info("Updating case {}", caseDetails.getId());
        final CaseDetails result = apply(
            authorisation,
            eventId,
            eventSummary,
            eventDescription,
            caseType,
            caseDetails.getId(),
            caseDetails.getJurisdiction(),
            false);
        log.info("Updated case {} isnull(result): {}", caseDetails.getId(), result == null);
        return result;
    }

    public CaseDetails rollback(
            final String authorisation,
            final String eventId,
            final String eventSummary,
            final String eventDescription,
            final String caseType,
            final CaseDetails caseDetails) {
        log.info("Rollback case {}", caseDetails.getId());
        final CaseDetails result = apply(
            authorisation,
            eventId,
            eventSummary,
            eventDescription,
            caseType,
            caseDetails.getId(),
            caseDetails.getJurisdiction(),
            true);
        log.info("Rolled back case {} isnull(result): {}", caseDetails.getId(), result == null);
        return result;
    }


    /* Note we cannot trust the CaseDetails passed to the update/rollback as there may have been another event
     * between the last data written into elasticsearch and now.
     */
    CaseDetails apply(
        final String authorisation,
        final String eventId,
        final String eventSummary,
        final String eventDescription,
        final String caseType,
        final Long caseId,
        final String caseJurisdiction,
        final boolean isRollback) {
        UserDetails userDetails = idamClient.getUserDetails(AuthUtil.getBearerToken(authorisation));

        StartEventResponse startEventResponse = coreCaseDataApi.startEventForCaseWorker(
            AuthUtil.getBearerToken(authorisation),
            authTokenGenerator.generate(),
            userDetails.getId(),
            caseJurisdiction,
            caseType,
            caseId.toString(),
            eventId);

        if (startEventResponse == null) {
            log.warn("Case {}: Unable to start event {}", caseId, eventId);
            return null;
        }
        CaseDetails caseDetails = startEventResponse.getCaseDetails();
        if (caseDetails == null) {
            log.warn("Case {}: No case details present in startEventResponse", caseId);
            return null;
        }

        final Event event = Event.builder()
            .id(startEventResponse.getEventId())
            .summary(eventSummary)
            .description(eventDescription)
            .build();

        final CaseDataContent caseDataContent = CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(event)
            .data(caseDetails.getData())
            .build();

        return coreCaseDataApi.submitEventForCaseWorker(
            AuthUtil.getBearerToken(authorisation),
            authTokenGenerator.generate(),
            userDetails.getId(),
            caseDetails.getJurisdiction(),
            caseDetails.getCaseTypeId(),
            caseDetails.getId().toString(),
            true,
            caseDataContent);
    }
}
