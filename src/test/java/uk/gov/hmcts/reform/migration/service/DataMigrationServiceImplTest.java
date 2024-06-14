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
import uk.gov.hmcts.reform.domain.common.CollectionMember;
import uk.gov.hmcts.reform.domain.common.HandoffReason;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class DataMigrationServiceImplTest {
    @Mock
    private CoreCaseDataApi coreCaseDataApi;
    @InjectMocks
    private DataMigrationServiceImpl service;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DataMigrationServiceImpl();
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
        Map<String, Object> result = service.migrate(1L, data);
        assertNotNull(result);
        assertEquals(data, result);
    }

    @Test
    public void shouldReturnNullWhenDataIsNotPassed() {
        Map<String, Object> result = service.migrate(1L, null);
        assertNull(result);
        assertEquals(null, result);
    }

    @Test
    public void shouldMigrateHandoffReasonCases() {
        Map<String, Object> data = new HashMap<>();
        data.put("caseHandedOffToLegacySite", "Yes");

        Map<String, Object> expectedData = new HashMap<>();
        List<CollectionMember<HandoffReason>> reason = new ArrayList<>();
        reason.add(new CollectionMember<>(null, HandoffReason.builder().handoffReason("Spreadsheet").build()));
        expectedData.put("boHandoffReasonList", reason);
        expectedData.put("caseHandedOffToLegacySite", "Yes");

        Map<String, Object> result = service.migrate(1L, data);
        assertEquals(expectedData, result);
    }

    @Test
    public void shouldNotMigrateHandoffReasonCases() {
        Map<String, Object> data = new HashMap<>();
        data.put("caseHandedOffToLegacySite", "No");

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("boHandoffReasonList", null);
        expectedData.put("caseHandedOffToLegacySite", "No");

        Map<String, Object> result = service.migrate(1L, data);
        assertEquals(expectedData, result);
    }
}
