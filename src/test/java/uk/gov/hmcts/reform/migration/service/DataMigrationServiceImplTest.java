package uk.gov.hmcts.reform.migration.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class DataMigrationServiceImplTest {
    @Mock
    private OrganisationsRetrievalService organisationsRetrievalService;

    private DataMigrationServiceImpl service = new DataMigrationServiceImpl(organisationsRetrievalService);

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
        Map<String, Object> result = service.migrate(1L, data, "token");
        assertNotNull(result);
        assertEquals(data, result);
    }

    @Test
    public void shouldReturnNullWhenDataIsNotPassed() {
        Map<String, Object> result = service.migrate(1L, null, "token");
        assertNull(result);
        assertEquals(null, result);
    }

    @Test
    public void shouldMigrateCasesOfSolGopTrustCorp() {
        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Solicitor");
        data.put("caseType", "gop");
        data.put("titleAndClearingType", "TCTTrustCorpResWithSDJ");
        Map<String, Object> result = service.migrate(1L, data, "token");
        assertEquals("Yes",result.get("caseHandedOffToLegacySite"));
    }

    @Test
    public void shouldMigrateCasesOfSolIntestacyDeceasedDomicileInEngWales() {
        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Solicitor");
        data.put("caseType", "intestacy");
        data.put("deceasedDomicileInEngWales", "No");
        Map<String, Object> result = service.migrate(1L, data, "token");
        assertEquals("Yes",result.get("caseHandedOffToLegacySite"));
    }

    @Test
    public void shouldMigrateCasesOfSolAdmonWillWillAccess() {
        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Solicitor");
        data.put("caseType", "admonWill");
        data.put("willAccessOriginal", "No");
        data.put("willAccessNotarial", "Yes");
        Map<String, Object> result = service.migrate(1L, data, "token");
        assertEquals("Yes",result.get("caseHandedOffToLegacySite"));
    }

    @Test
    public void shouldMigrateCasesOfSolIntestacySolsApplicantRelationshipToDeceased() {
        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Solicitor");
        data.put("caseType", "intestacy");
        data.put("solsApplicantRelationshipToDeceased", "ChildAdopted");
        Map<String, Object> result = service.migrate(1L, data, "token");
        assertEquals("Yes",result.get("caseHandedOffToLegacySite"));
    }

    @Test
    public void shouldMigrateCasesOfPersonalIntestacy() {
        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Personal");
        data.put("caseType", "intestacy");
        data.put("primaryApplicantRelationshipToDeceased", "adoptedChild");
        data.put("primaryApplicantAdoptionInEnglandOrWales", "Yes");
        Map<String, Object> result = service.migrate(1L, data, "token");
        assertEquals("Yes",result.get("caseHandedOffToLegacySite"));
    }

    @Test
    public void shouldMigrateCasesOfOtherToDefalult() {
        Map<String, Object> data = new HashMap<>();
        data.put("applicationType","Personal");
        data.put("caseType", "intestacy");
        data.put("primaryApplicantRelationshipToDeceased", "adoptedChild");
        data.put("primaryApplicantAdoptionInEnglandOrWales", "No");
        Map<String, Object> result = service.migrate(1L, data, "token");
        assertEquals(result.get("caseHandedOffToLegacySite"),"No");
    }
}
