package uk.gov.hmcts.reform.migration.reimpl.migrations.removedecddob;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Configuration
@PropertySource("classpath:application.properties")
public class RemoveDeceasedDobConfig {
    private final Optional<Set<CaseSummary>> caseRefs;

    public RemoveDeceasedDobConfig(
            @Value("${removeDecdDob.caseRefs}")
            final String caseRefs) {
        this.caseRefs = parseCaseReferences(caseRefs);
    }

    public Optional<Set<CaseSummary>> getCaseRefs() {
        return caseRefs;
    }

    static Optional<Set<CaseSummary>> parseCaseReferences(final String caseRefs) {
        if (StringUtils.isBlank(caseRefs)) {
            log.info("Returning empty optional for cases config input [{}]", caseRefs);
            return Optional.empty();
        }
        Set<CaseSummary> caseSummarySet = new HashSet<>();
        final String[] splitCasesConfig = caseRefs
            .trim()
            .split(",");
        for (String splitCase : splitCasesConfig) {
            try {
                final Long caseId = Long.parseLong(splitCase);
                final CaseType caseType = CaseType.GRANT_OF_REPRESENTATION;
                final CaseSummary caseSummary = new CaseSummary(caseId, caseType);
                log.info("Adding case to search for: [{}]", caseSummary);
                caseSummarySet.add(caseSummary);
            } catch (RuntimeException e) {
                final String errMessage = new StringBuilder()
                    .append("Error reading configuration for removing deceased dob config. Found caseId [")
                    .append(splitCase)
                    .append("] which could not be mapped to a case reference. Full input was [")
                    .append(caseRefs)
                    .append("]")
                    .toString();
                log.error(errMessage, e);
                throw new IllegalArgumentException(errMessage, e);
            }
        }
        return Optional.of(caseSummarySet);
    }
}
