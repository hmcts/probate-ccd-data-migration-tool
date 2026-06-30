package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5539;


import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Getter
@Configuration
@PropertySource("classpath:application.properties")
public class Dtspb5539Config {

    private final LocalDate rollbackDate;
    private final List<CaseType> caseTypes;
    private final String hmctsId;

    public Dtspb5539Config(

        @Value("${dtspb5539.rollback_date}")
        LocalDate rollbackDate,
        @Value("#{'${dtspb5539.case_types}'.split(',')}")
        List<String> caseTypes,
        @Value("${dtspb5539.supplementary-data.hmctsid}")
        String hmctsId) {
        this.rollbackDate = Objects.requireNonNull(
            rollbackDate,
            "dtspb5539.rollback_date must not be null"
        );



        if (hmctsId == null || hmctsId.isBlank()) {
            throw new IllegalArgumentException(
                "dtspb5539.supplementary-data.hmctsid must not be null or blank"
            );
        }
        this.hmctsId = hmctsId;

        Objects.requireNonNull(
            caseTypes,
            "dtspb5539.case_types must not be null"
        );
        if (caseTypes.isEmpty()) {
            throw new IllegalArgumentException(
                "dtspb5539.case_types must not be empty"
            );
        }
        this.caseTypes = caseTypes.stream()
            .map(String::trim)
            .map(CaseType::fromCcdValue)
            .toList();
    }

}
