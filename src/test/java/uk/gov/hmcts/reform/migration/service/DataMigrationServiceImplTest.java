package uk.gov.hmcts.reform.migration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.migration.model.Dtspb4583Dates;
import uk.gov.hmcts.reform.migration.service.dtspb4583.Dtspb4583DataService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataMigrationServiceImplTest {
    private static final LocalDateTime LAST_MODIFIED = LocalDateTime.now(ZoneOffset.UTC).minusYears(3);

    Dtspb4583DataService dtspb4583DataServiceMock;

    private DataMigrationServiceImpl service;

    @BeforeEach
    void setUp() {
        dtspb4583DataServiceMock = mock(Dtspb4583DataService.class);
        service = new DataMigrationServiceImpl(dtspb4583DataServiceMock);
    }

    @Test
    void shouldReturnTrueForCaseDetailsPassed() {
        when(dtspb4583DataServiceMock.get(any()))
            .thenReturn(Optional.of(new Dtspb4583Dates("", "")));
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
