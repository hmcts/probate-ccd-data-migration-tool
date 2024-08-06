package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.Organisation;
import uk.gov.hmcts.reform.domain.common.OrganisationPolicy;

import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private static final String POLICY_ROLE_APPLICANT_SOLICITOR = "[APPLICANTSOLICITOR]";
    private static final String APPLICANT_ORG_POLICY = "applicantOrganisationPolicy";

    @Override
    public Predicate<CaseDetails> accepts() {
        return caseDetails -> caseDetails == null ? false : true;
    }

    @Override
    public Map<String, Object> rollback(Map<String, Object> data) {
        if (data == null) {
            return null;
        } else {
            data.put(APPLICANT_ORG_POLICY, null);
        }
        return data;
    }

    @Override
    public Map<String, Object> migrate(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        if (null == data.get(APPLICANT_ORG_POLICY)) {
            OrganisationPolicy policy = OrganisationPolicy.builder()
                .organisation(Organisation.builder()
                    .organisationID(null)
                    .organisationName(null)
                    .build())
                .orgPolicyReference(null)
                .orgPolicyCaseAssignedRole(POLICY_ROLE_APPLICANT_SOLICITOR)
                .build();
            data.put(APPLICANT_ORG_POLICY, policy);
        }
        return data;
    }
}
