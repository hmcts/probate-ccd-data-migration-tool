package uk.gov.hmcts.reform.migration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMigrationServiceImplTest {
    private static final LocalDateTime LAST_MODIFIED = LocalDateTime.now(ZoneOffset.UTC).minusYears(3);

    private DataMigrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataMigrationServiceImpl();
    }

    @Test
    void shouldReturnTrueForCaseDetailsPassed() {
        CaseDetails caseDetails = CaseDetails.builder()
            .id(1234L)
            .build();
        assertTrue(service.accepts().test(caseDetails));
    }

    @Test
    void shouldReturnFalseForCaseDetailsNull() {
        assertFalse(service.accepts().test(null));
    }

    @Test
    void shouldReturnPassedDataWhenMigrateCalled() {
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
    void shouldReturnNullWhenDataIsNotPassed() {
        Map<String, Object> result = service.migrate(null);
        assertNull(result);
        assertEquals(null, result);
    }
}
