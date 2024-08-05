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
import uk.gov.hmcts.reform.domain.common.OrganisationEntityResponse;
import uk.gov.hmcts.reform.domain.common.OrganisationPolicy;

import java.time.LocalDateTime;
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
    private static final String RANDOM_EVENT = "createCaseFromBulkScan";
    private static final LocalDateTime dateTime = LocalDateTime.of(2024, 1, 1, 1,
        1, 1, 1);

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DataMigrationServiceImpl(auditEventService);
        AuditEvent mockedEvent = AuditEvent.builder()
            .id(RANDOM_EVENT)
            .userId("123")
            .createdDate(dateTime)
            .build();
        when(auditEventService.getLatestAuditEventByName(anyString(), anyList(), anyString(), anyString()))
            .thenReturn(Optional.of(mockedEvent));
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
    }

    @Test
    public void shouldMigrateLastModifiedDateForDormantWithCreateDate() {
        Map<String, Object> data = new HashMap<>();
        data.put("lastModifiedDateForDormant", null);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("lastModifiedDateForDormant", dateTime);

        Map<String, Object> result = service.migrate(1L, data, "token", "serviceToken");

        assertEquals(expectedData, result);
    }

    @Test
    public void shouldNotMigrateCaseWithExcludedEvent() {
        Map<String, Object> data = new HashMap<>();
        data.put("lastModifiedDateForDormant", null);
        when(auditEventService.getLatestAuditEventByName(anyString(), anyList(), anyString(), anyString()))
            .thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> service.migrate(1L, data, "token", "serviceToken"));
        assertEquals("Could not find any event other than [boHistoryCorrection, boCorrection] "
            + "event in audit", exception.getMessage());
    }
}
