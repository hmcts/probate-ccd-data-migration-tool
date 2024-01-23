package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.AuditEvent;
import uk.gov.hmcts.reform.domain.common.Organisation;
import uk.gov.hmcts.reform.domain.common.OrganisationEntityResponse;
import uk.gov.hmcts.reform.domain.common.OrganisationPolicy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private final AuditEventService auditEventService;
    private final OrganisationApi organisationApi;
    private final CoreCaseDataApi coreCaseDataApi;
    private List<String> solicitorEvent = Arrays.asList("solicitorCreateApplication", "solicitorCreateCaveat");

    @Override
    public Predicate<CaseDetails> accepts() {
        return caseDetails -> caseDetails == null ? false : true;
    }

    @Override
    public Map<String, Object> rollback(Long id, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        } else {
            OrganisationPolicy policy = OrganisationPolicy.builder()
                .organisation(Organisation.builder()
                    .organisationID(null)
                    .organisationName(null)
                    .build())
                .orgPolicyReference(null)
                .orgPolicyCaseAssignedRole(null)
                .build();
            data.put("applicantOrganisationPolicy", policy);
            log.info("Org policy {}", data.get("applicantOrganisationPolicy"));
            Map<String, Map<String, Map<String, Object>>> supplementaryData = new HashMap<>();
            //coreCaseDataApi.submitSupplementaryData(userToken, authToken, id.toString(), supplementaryData);
        }
        return data;
    }

    @Override
    public Map<String, Object> migrate(Long caseId, Map<String, Object> data, String userToken, String authToken) {
        if (data == null) {
            return null;
        }
        AuditEvent auditEvent = getAuditEvent(caseId, userToken, authToken);
        log.info("Audit events {}", auditEvent);
        OrganisationEntityResponse response = getOrganisationDetails(userToken, authToken, auditEvent.getUserId());
        log.info("organisation response {}", response);
        if (response != null) {
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
            Map<String, Object> usersMap = new HashMap<>();
            usersMap.put("orgs_assigned_users." + response.getOrganisationIdentifier(), 1);
            Map<String, Map<String, Map<String, Object>>> supplementaryData = new HashMap<>();
            supplementaryData.put("supplementary_data_updates", Map.of("$set", usersMap));
            coreCaseDataApi.submitSupplementaryData(userToken, authToken,
                caseId.toString(), supplementaryData);
        }
        return data;
    }

    private AuditEvent getAuditEvent(Long caseId, String userToken, String authToken) {
        return auditEventService.getLatestAuditEventByName(caseId.toString(), solicitorEvent,
                userToken, authToken).orElseThrow(() -> new IllegalStateException(String
            .format("Could not find %s event in audit", solicitorEvent)));
    }

    private OrganisationEntityResponse getOrganisationDetails(String userToken, String authToken, String userId) {
        return organisationApi.findOrganisationOfSolicitor(userToken, authToken, userId);
    }
}
