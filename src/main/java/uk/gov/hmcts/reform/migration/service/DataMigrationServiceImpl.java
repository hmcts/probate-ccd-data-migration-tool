package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.AuditEvent;
import uk.gov.hmcts.reform.domain.common.Organisation;
import uk.gov.hmcts.reform.domain.common.OrganisationEntityResponse;
import uk.gov.hmcts.reform.domain.common.OrganisationPolicy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private final AuditEventService auditEventService;
    private final OrganisationApi organisationApi;
    private List<String> solicitorEvent = Arrays.asList("solicitorCreateApplication", "solicitorCreateCaveat");
    @Override
    public Predicate<CaseDetails> accepts() {
        return caseDetails -> caseDetails == null ? false : true;
    }

    @Override
    public Map<String, Object> rollback(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        //data.remove("caseHandedOffToLegacySite");
        //data.put("caseHandedOffToLegacySite",null);
        return data;
    }

    @Override
    public Map<String, Object> migrate(Long caseId, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        }
        AuditEvent auditEvent = getAuditEvent(caseId, data, userToken, authToken);
        log.info("Audit events {}", auditEvent);
        OrganisationEntityResponse response = getOrganisationDetails(userToken, authToken, auditEvent.getUserId());
        log.info("organisation response {}", response);
        if (data.get("applicantOrganisationPolicy") == null) {
            OrganisationPolicy policy = OrganisationPolicy.builder()
                .organisation(Organisation.builder()
                    .organisationID(response.getOrganisationIdentifier())
                    .organisationName(response.getName())
                    .build())
                .orgPolicyReference(null)
                .orgPolicyCaseAssignedRole("[APPLICANTSOLICITOR]")
                .build();
            data.put("applicantOrganisationPolicy", policy);
            log.info("Org policy {}", data.get("applicantOrganisationPolicy"));
        }
        return data;
    }

    private AuditEvent getAuditEvent(Long caseId, Map<String, Object> data, String userToken, String authToken) {
        return auditEventService.getLatestAuditEventByName(caseId.toString(), solicitorEvent,
                userToken, authToken).orElseThrow(() -> new IllegalStateException(String
            .format("Could not find %s event in audit", solicitorEvent)));
    }

    private OrganisationEntityResponse getOrganisationDetails(String userToken, String authToken, String userId) {
        return organisationApi.findOrganisationOfSolicitor(userToken, authToken, userId);
    }

    private boolean shouldCaseToHandedOffToLegacySite(Map<String, Object> caseData) {
        if (caseData.containsKey("applicationType") && caseData.get("applicationType").equals("Solicitor")
            && (caseData.containsKey("titleAndClearingType")
            && (caseData.get("titleAndClearingType").equals("TCTTrustCorpResWithSDJ")
            || caseData.get("titleAndClearingType").equals("TCTTrustCorpResWithApp")))
        ) {
            return true;
        }
        if (caseData.containsKey("applicationType") && caseData.get("applicationType").equals("Solicitor")
            && (caseData.containsKey("caseType")
            && (caseData.get("caseType").equals("gop")
            || caseData.get("caseType").equals("admonWill")
            || caseData.get("caseType").equals("intestacy")))
            && (caseData.containsKey("deceasedDomicileInEngWales")
            && caseData.get("deceasedDomicileInEngWales").equals("No"))
        ) {
            return true;
        }
        if (caseData.containsKey("applicationType") && caseData.get("applicationType").equals("Solicitor")
            && (caseData.containsKey("caseType")
            && (caseData.get("caseType").equals("gop")
            || caseData.get("caseType").equals("admonWill")
            || caseData.get("caseType").equals("intestacy")))
            && (caseData.containsKey("willAccessOriginal") && caseData.get("willAccessOriginal").equals("No"))
            && (caseData.containsKey("willAccessNotarial") && caseData.get("willAccessNotarial").equals("Yes"))
        ) {
            return true;
        }
        if (caseData.containsKey("applicationType") && caseData.get("applicationType").equals("Solicitor")
            && caseData.containsKey("caseType") && caseData.get("caseType").equals("intestacy")
            && caseData.containsKey("solsApplicantRelationshipToDeceased")
            && caseData.get("solsApplicantRelationshipToDeceased").equals("ChildAdopted")
        ) {
            return true;
        }
        if (caseData.containsKey("applicationType") && caseData.get("applicationType").equals("Personal")
            && caseData.containsKey("caseType") && caseData.get("caseType").equals("intestacy")
            && caseData.containsKey("primaryApplicantRelationshipToDeceased")
            && caseData.get("primaryApplicantRelationshipToDeceased").equals("adoptedChild")
            && caseData.containsKey("primaryApplicantAdoptionInEnglandOrWales")
            && caseData.get("primaryApplicantAdoptionInEnglandOrWales").equals("Yes")
        ) {
            return true;
        }
        return false;
    }

}
