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
import uk.gov.hmcts.reform.migration.model.Dtspb4583Dates;
import uk.gov.hmcts.reform.migration.service.dtspb4583.Dtspb4583DataService;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
@Service
public class CoreCaseDataService {

    private final IdamClient idamClient;
    private final AuthTokenGenerator authTokenGenerator;
    private final CoreCaseDataApi coreCaseDataApi;
    private final Dtspb4583DataService dtspb4583DataService;

    static final String APPL_SUBMIT_DATE = "applicationSubmittedDate";

    public CoreCaseDataService(
            final IdamClient idamClient,
            final AuthTokenGenerator authTokenGenerator,
            final CoreCaseDataApi coreCaseDataApi,
            final Dtspb4583DataService dtspb4583DataService) {
        this.idamClient = idamClient;
        this.authTokenGenerator = authTokenGenerator;
        this.coreCaseDataApi = coreCaseDataApi;
        this.dtspb4583DataService = dtspb4583DataService;
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
            Dtspb4583Dates::incorrect,
            Dtspb4583Dates::correct);
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
            Dtspb4583Dates::correct,
            Dtspb4583Dates::incorrect);
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
        final Function<Dtspb4583Dates, String> expectedCurrent,
        final Function<Dtspb4583Dates, String> setTo) {
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

        final Optional<Dtspb4583Dates> caseDatesOpt = dtspb4583DataService.get(caseDetails.getId());
        if (caseDatesOpt.isEmpty()) {
            log.warn("Case {}: Attempting to update without entry in DTSPB-4583 data", caseDetails.getId());
            return null;
        }
        final Dtspb4583Dates caseDates = caseDatesOpt.get();

        final Map<String, Object> updatedData = caseDetails.getData();

        final Object currentSubmDateObj = updatedData.get(APPL_SUBMIT_DATE);
        if (currentSubmDateObj == null) {
            log.warn("Case {}: No application submitted date found in case data", caseDetails.getId());
            return null;
        }
        if (!(currentSubmDateObj instanceof String)) {
            log.warn("Case {}: application submitted date is not of type String but {} (toString: {})",
                caseDetails.getId(),
                currentSubmDateObj.getClass().getName(),
                currentSubmDateObj);
            return null;
        }
        final String currentSubmDate = (String) currentSubmDateObj;

        final String expSubmDate = expectedCurrent.apply(caseDates);
        if (!(currentSubmDate.equals(expSubmDate))) {
            log.warn(
                "Case {}: current application submitted date does not match expected value: current [{}] expected [{}]",
                caseDetails.getId(),
                currentSubmDate,
                expSubmDate);
            return null;
        }
        final String correctDate = setTo.apply(caseDates);
        log.info("Case {}: Setting application submitted date to [{}]",
            caseDetails.getId(),
            correctDate);

        updatedData.put(APPL_SUBMIT_DATE, correctDate);

        final Event event = Event.builder()
            .id(startEventResponse.getEventId())
            .summary(eventSummary)
            .description(eventDescription)
            .build();

        final CaseDataContent caseDataContent = CaseDataContent.builder()
            .eventToken(startEventResponse.getToken())
            .event(event)
            .data(updatedData)
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
