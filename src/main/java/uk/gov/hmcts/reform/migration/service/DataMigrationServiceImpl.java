package uk.gov.hmcts.reform.migration.service;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.Map;
import java.util.function.Predicate;

@Service
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {

    @Override
    public Predicate<CaseDetails> accepts() {
        return caseDetails -> caseDetails == null ? false : true;
    }

    @Override
    public Map<String, Object> migrate(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        if (setCaseToHandedOffToLegacySite(data)) {
            data.put("caseHandedOffToLegacySite","Yes");
        } else {
            data.put("caseHandedOffToLegacySite","No");
        }
        return data;
    }

    private boolean setCaseToHandedOffToLegacySite(Map<String, Object> caseData) {
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
