package uk.gov.hmcts.reform.migration.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.AuditEvent;
import uk.gov.hmcts.reform.domain.common.Organisation;
import uk.gov.hmcts.reform.domain.common.OrganisationEntityResponse;
import uk.gov.hmcts.reform.domain.common.OrganisationPolicy;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class DataMigrationServiceImplTest {
    @Mock
    private AuditEventService auditEventService;
    @Mock
    private OrganisationApi organisationApi;
    private OrganisationEntityResponse organisationEntityResponse;
    @Mock
    private CoreCaseDataApi coreCaseDataApi;
    @Mock
    private AuditEvent event;
    @InjectMocks
    private DataMigrationServiceImpl service;
    private OrganisationPolicy policy;
    private static final String POLICY_ROLE_APPLICANT_SOLICITOR = "[APPLICANTSOLICITOR]";
    private static final String APPLICANT_ORG_POLICY = "applicantOrganisationPolicy";
    private OrganisationPolicy organisationPolicy;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DataMigrationServiceImpl();
        organisationPolicy = OrganisationPolicy.builder()
            .organisation(Organisation.builder()
                .organisationID(null)
                .organisationName(null)
                .build())
            .orgPolicyReference(null)
            .orgPolicyCaseAssignedRole(POLICY_ROLE_APPLICANT_SOLICITOR)
            .build();
    }

    @Test
    public void shouldReturnTrueForCaseDetailsPassed() {
        CaseDetails caseDetails = CaseDetails.builder()
            .id(1234L)
            .build();
        assertTrue(service.accepts().test(caseDetails));
    }

    @Test
    public void shouldReturnFalseForCaseDetailsNull() {
        assertFalse(service.accepts().test(null));
    }

    @Test
    public void shouldReturnPassedDataWhenMigrateCalled() {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> result = service.migrate(data);
        assertNotNull(result);
        assertEquals(data, result);
    }

    @Test
    public void shouldReturnNullWhenDataIsNotPassed() {
        Map<String, Object> result = service.migrate(null);
        assertNull(result);
        assertEquals(null, result);
    }

    @Test
    public void shouldMigrateOrgPolicy() {
        Map<String, Object> data = new HashMap<>();
        data.put("applicantOrganisationPolicy", null);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("applicantOrganisationPolicy", organisationPolicy);

        Map<String, Object> result = service.migrate(data);
        assertEquals(expectedData, result);
    }
}
