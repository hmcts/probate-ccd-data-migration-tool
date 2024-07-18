package uk.gov.hmcts.reform.migration.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.AuditEvent;
import uk.gov.hmcts.reform.domain.common.OrganisationEntityResponse;
import uk.gov.hmcts.reform.domain.common.OrganisationPolicy;

import java.time.LocalDate;
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
    private static final String CREATE_CASE_FROM_BULKSCAN_EVENT = "createCaseFromBulkScan";


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
    public void shouldMigrateSubDateToCreateDate() {
        String date = LocalDate.now().toString();

        Map<String, Object> data = new HashMap<>();
        data.put("applicationSubmittedDate", null);
        data.put("createdDate", date);

        Map<String, Object> expectedData = new HashMap<>();
        Object createdDate = data.get("createdDate");
        expectedData.put("applicationSubmittedDate", createdDate);

        Map<String, Object> result = service.migrate(1L, data, "token", "serviceToken");
        assertEquals(expectedData.get("applicationSubmittedDate"), result.get("applicationSubmittedDate"));
    }

    @Test
    public void shouldNotMigrateAsDataDoesNotPassCondition() {
        String date = LocalDate.now().toString();

        Map<String, Object> data = new HashMap<>();
        data.put("applicationSubmittedDate", date);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("applicationSubmittedDate", date);

        Map<String, Object> result = service.migrate(1L, data, "token", "serviceToken");

        assertEquals(expectedData, result);
    }
}
