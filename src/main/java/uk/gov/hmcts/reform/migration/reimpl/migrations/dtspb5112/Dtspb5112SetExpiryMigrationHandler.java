package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CaseDetails;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112.Dtspb5112Constants.MIGRATION_EVENT;

@Component
public class Dtspb5112SetExpiryMigrationHandler implements MigrationHandler {
    static final String SUMMARY = "DTSPB-5112 - Set missing caveat expiry date";
    static final String DESCRIPTION = "Set expiryDate to six months after successful payment";

    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb5112ElasticQueries queries;
    private final Dtspb5112PaymentService paymentService;
    private final Dtspb5112MigrationSupport support;

    public Dtspb5112SetExpiryMigrationHandler(
        ElasticSearchHandler elasticSearchHandler,
        Dtspb5112ElasticQueries queries,
        Dtspb5112PaymentService paymentService,
        Dtspb5112MigrationSupport support
    ) {
        this.elasticSearchHandler = elasticSearchHandler;
        this.queries = queries;
        this.paymentService = paymentService;
        this.support = support;
    }

    @Override
    public Set<CaseSummary> getCandidateCases(UserToken userToken, S2sToken s2sToken) {
        return elasticSearchHandler.searchCases(
            Dtspb5112Constants.S2_ID,
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            (pageSize, from) -> queries.missingExpiry(pageSize, Dtspb5112Constants.LIVE_STATES, from)
        );
    }

    @Override
    public MigrationEvent startEventForCase(CaseSummary caseSummary, UserToken userToken, S2sToken s2sToken) {
        return support.start(caseSummary, userToken, s2sToken, MIGRATION_EVENT);
    }

    @Override
    public boolean shouldMigrateCase(MigrationEvent migrationEvent) {
        CaseDetails details = support.requireCaseDetails(migrationEvent);
        Object expiry = details.getData().get(Dtspb5112Constants.EXPIRY_DATE);
        return Dtspb5112Constants.LIVE_STATES.contains(details.getState())
            && (expiry == null || expiry.toString().isBlank())
            && paymentService.successfulPaymentDate(migrationEvent).isPresent();
    }

    @Override
    public boolean migrate(MigrationEvent migrationEvent) {
        LocalDate paymentDate = paymentService.successfulPaymentDate(migrationEvent).orElse(null);
        if (paymentDate == null) {
            return false;
        }

        Map<String, Object> data = support.mutableData(migrationEvent);
        data.put(Dtspb5112Constants.EXPIRY_DATE, paymentDate.plusMonths(6).toString());

        // No migration callback metadata is required here: this event only sets a non-null field.
        return support.submit(migrationEvent, data, SUMMARY, DESCRIPTION);
    }
}
