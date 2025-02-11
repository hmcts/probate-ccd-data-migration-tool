package uk.gov.hmcts.reform.migration.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.domain.common.TTL;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class DataMigrationServiceImplTest {
    private static final String TTL_FIELD_NAME = "TTL";
    private static final String NO = "No";
    private static final LocalDateTime LAST_MODIFIED = LocalDateTime.now(ZoneOffset.UTC).minusYears(3);
    private TTL ttl;

    @InjectMocks
    private DataMigrationServiceImpl service;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DataMigrationServiceImpl();
        ttl = TTL.builder()
            .systemTTL(LocalDate.from(LAST_MODIFIED.plusDays(14)))
            .suspended(NO)
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
        CaseDetails caseDetails = CaseDetails.builder()
            .id(1234L)
            .data(data)
            .lastModified(LAST_MODIFIED)
            .build();
        Map<String, Object> result = service.migrate(caseDetails);
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
    public void shouldMigrateTtl() {
        Map<String, Object> data = new HashMap<>();
        data.put(TTL_FIELD_NAME, null);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put(TTL_FIELD_NAME, ttl);

        CaseDetails caseDetails = CaseDetails.builder()
            .id(1234L)
            .data(data)
            .lastModified(LAST_MODIFIED)
            .build();
        Map<String, Object> result = service.migrate(caseDetails);
        assertEquals(expectedData, result);
    }
}
