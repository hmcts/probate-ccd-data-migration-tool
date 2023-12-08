package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.Organisation;
import uk.gov.hmcts.reform.domain.common.OrganisationEntityResponse;
import uk.gov.hmcts.reform.domain.common.OrganisationPolicy;

import java.util.Map;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private final OrganisationsRetrievalService organisationsRetrievalService;

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
    public Map<String, Object> migrate(Long id, Map<String, Object> data, String token) {
        if (data == null) {
            return null;
        }
        OrganisationEntityResponse organisationEntityResponse = null;
        if (null != token) {
            organisationEntityResponse = organisationsRetrievalService.getOrganisationEntity(
                id.toString(), token);
        }
        if (null != organisationEntityResponse) {
            OrganisationPolicy policy = OrganisationPolicy.builder()
                .organisation(Organisation.builder()
                    .organisationID(organisationEntityResponse.getOrganisationIdentifier())
                    .organisationName(organisationEntityResponse.getName())
                    .build())
                .orgPolicyReference(null)
                .orgPolicyCaseAssignedRole("[APPLICANTSOLICITOR]")
                .build();
            data.put("applicantOrganisationPolicy", policy);
        }
        /*if (shouldCaseToHandedOffToLegacySite(data)) {
            data.put("caseHandedOffToLegacySite","Yes");
        } else {
            data.put("caseHandedOffToLegacySite","No");
        }*/
        return data;
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
