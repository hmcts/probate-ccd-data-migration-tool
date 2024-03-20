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
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private static final String SOLICITOR_EVENT = "solicitorCreateApplication";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DataMigrationServiceImpl(auditEventService, organisationApi,
            coreCaseDataApi);
        AuditEvent mockedEvent = AuditEvent.builder().id(SOLICITOR_EVENT).userId("123").build();
        when(auditEventService.getLatestAuditEventByName(anyString(), anyList(), anyString(), anyString()))
            .thenReturn(Optional.of(mockedEvent));
        organisationEntityResponse = OrganisationEntityResponse.builder()
            .organisationIdentifier("ABC").name("Org2 name").build();
        when(organisationApi.findOrganisationOfSolicitor(anyString(), anyString(), anyString()))
            .thenReturn(organisationEntityResponse);
        policy = OrganisationPolicy.builder()
            .organisation(Organisation.builder()
                .organisationID("ABC")
                .organisationName("Org2 name")
                .build())
            .orgPolicyReference(null)
            .orgPolicyCaseAssignedRole("[APPLICANTSOLICITOR]")
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
        Map<String, Object> result = service.migrate(1L, data, "token", "serviceToken");
        assertNotNull(result);
        assertEquals(data, result);
    }

    @Test
    public void shouldReturnNullWhenDataIsNotPassed() {
        Map<String, Object> result = service.migrate(1L, null, "token", "serviceToken");
        assertNull(result);
        assertEquals(null, result);
    }

    @Test
    public void shouldMigrateCasesWithOrgPolicy() {
        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Solicitor");
        data.put("caseType", "GrantOfRepresentation");
        data.put("solsSolicitorWillSignSOT", "Yes");
        Map<String, Object> result = service.migrate(1L, data, "token", "serviceToken");
        assertEquals(policy, result.get("applicantOrganisationPolicy"));
        verify(auditEventService, times(1)).getLatestAuditEventByName(anyString(),
            anyList(), anyString(), anyString());
        verify(organisationApi, times(1)).findOrganisationOfSolicitor(anyString(),
            anyString(), anyString());
    }

    @Test
    public void shouldNotMigrateCasesWhenResponseIsNull() {
        when(organisationApi.findOrganisationOfSolicitor(anyString(), anyString(), anyString()))
            .thenReturn(null);

        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Solicitor");
        data.put("caseType", "GrantOfRepresentation");
        data.put("solsSolicitorWillSignSOT", "Yes");
        Map<String, Object> result = service.migrate(1L, data, "token", "serviceToken");
        assertNull(result.get("applicantOrganisationPolicy"));
        verify(auditEventService, times(1)).getLatestAuditEventByName(anyString(),
            anyList(), anyString(), anyString());
        verify(organisationApi, times(1)).findOrganisationOfSolicitor(anyString(),
            anyString(), anyString());
    }

    @Test
    public void shouldMigrateCaveatCasesWithOrgPolicy() {
        AuditEvent mockedEvent = AuditEvent.builder().id("solicitorCreateCaveat").userId("123").build();
        when(auditEventService.getLatestAuditEventByName(anyString(), anyList(), anyString(), anyString()))
            .thenReturn(Optional.of(mockedEvent));

        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Solicitor");
        data.put("caseType", "Caveat");
        data.put("solsSolicitorFirmName", "Firm");
        Map<String, Object> result = service.migrate(1L, data, "token", "serviceToken");
        assertEquals(policy, result.get("applicantOrganisationPolicy"));
        verify(auditEventService, times(1)).getLatestAuditEventByName(anyString(),
            anyList(), anyString(), anyString());
        verify(organisationApi, times(1)).findOrganisationOfSolicitor(anyString(),
            anyString(), anyString());
    }

    @Test
    public void shouldThrowErrorWhenNoEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Solicitor");
        data.put("caseType", "gop");
        data.put("solsSolicitorWillSignSOT", "Yes");
        when(auditEventService.getLatestAuditEventByName(anyString(), anyList(), anyString(), anyString()))
            .thenReturn(Optional.empty());
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> service.migrate(1L, data, "token", "serviceToken"));

        assertEquals("Could not find [solicitorCreateApplication, solicitorCreateCaveat] event in audit",
            exception.getMessage());
        verify(auditEventService, times(1)).getLatestAuditEventByName(anyString(),
            anyList(), anyString(), anyString());
        verify(organisationApi, times(0)).findOrganisationOfSolicitor(anyString(),
            anyString(), anyString());
    }
}
