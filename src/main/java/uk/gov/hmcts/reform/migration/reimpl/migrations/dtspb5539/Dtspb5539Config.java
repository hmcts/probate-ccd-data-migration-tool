package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5539;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;

import java.time.LocalDate;
import java.util.List;

@Configuration
@PropertySource("classpath:application.properties")
public class Dtspb5539Config {

    private final LocalDate rollbackDate;
    private final List<CaseType> caseTypes;

    public Dtspb5539Config(
        @Value("${dtspb5539.rollback_date}")
        LocalDate rollbackDate,
        @Value("#{'${dtspb5539.case_types}'.split(',')}")
        List<String> caseTypes1) {
        this.rollbackDate = rollbackDate;
        this.caseTypes = caseTypes1.stream()
            .map(String::trim)
            .map(CaseType::fromCcdValue)
            .toList();
    }

    public LocalDate getRollbackDate() {
        return rollbackDate;
    }

    public List<CaseType> getCaseTypes() {
        return caseTypes;
    }
}
