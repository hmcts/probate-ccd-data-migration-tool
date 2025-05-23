package uk.gov.hmcts.reform.migration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.Predicate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMigrationServiceImpl implements DataMigrationService<Map<String, Object>> {
    private static final String EXPIRY_DATE = "expiryDate";

    @Override
    public Predicate<CaseDetails> accepts() {
        return cd -> {
            if (cd == null || cd.getData() == null) {
                return false;
            }
            Object raw = cd.getData().get(EXPIRY_DATE);
            if (raw == null) {
                return false;
            }
            try {
                LocalDate expiry = LocalDate.parse(raw.toString());
                LocalDate today = LocalDate.now();
                return expiry.isBefore(today);
            } catch (DateTimeParseException e) {
                final String errorMsg = String.format("Unable to parse expiry date for case: %s", cd.getId());
                log.error(errorMsg, e);
                return false;
            }
        };
    }

    @Override
    public Map<String, Object> rollback(Map<String, Object> data) {
        return data;
    }

    @Override
    public Map<String, Object> migrate(final CaseDetails caseDetails) {
        if (caseDetails == null) {
            return null;
        }

        return caseDetails.getData();
    }
}
