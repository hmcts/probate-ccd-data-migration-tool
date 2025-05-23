package uk.gov.hmcts.reform.migration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDate;
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
    private static final String EXPIRY_DATE = "expiryDate";

    private DataMigrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DataMigrationServiceImpl();
    }

    @Test
    void shouldReturnFalseForCaseDetailsPassed() {
        CaseDetails caseDetails = CaseDetails.builder()
            .id(1234L)
            .build();
        assertFalse(service.accepts().test(caseDetails));
    }

    @Test
    void shouldReturnsFalseForUnparsableExpiryDate() {
        CaseDetails cd = CaseDetails.builder()
            .id(2L)
            .data(Map.of(EXPIRY_DATE, "not-a-date"))
            .build();
        assertFalse(service.accepts().test(cd));
    }

    @Test
    void shouldReturnFalseForCaseDetailsNull() {
        assertFalse(service.accepts().test(null));
    }

    @Test
    void shouldReturnsTrueWhenExpiryDateIsBeforeToday() {
        String yesterday = LocalDate.now().minusDays(1).toString();
        CaseDetails cd = CaseDetails.builder()
            .id(3L)
            .data(Map.of(EXPIRY_DATE, yesterday))
            .build();
        assertTrue(service.accepts().test(cd), "Expiry date of " + yesterday + " should be accepted");
    }

    @Test
    void shouldReturnsFalseWhenExpiryDateIsAfterToday() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        CaseDetails cd = CaseDetails.builder()
            .id(3L)
            .data(Map.of(EXPIRY_DATE, tomorrow))
            .build();
        assertFalse(service.accepts().test(cd), "Expiry date of " + tomorrow + " should not be accepted");
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
    }
}
