package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.springframework.stereotype.Component;
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

import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112.Dtspb5112Constants.ROLLBACK_EVENT;

@Component
public class Dtspb5112SetExpiryRollbackMigrationHandler implements MigrationHandler {
    static final String SUMMARY = "DTSPB-5112 - Rollback caveat expiry date migration";
    static final String DESCRIPTION = "Remove expiryDate set by DTSPB-5112";

    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb5112Config config;
    private final Dtspb5112ElasticQueries queries;
    private final Dtspb5112PaymentService paymentService;
    private final Dtspb5112MigrationSupport support;
    private final Dtspb5112RollbackSupport rollbackSupport;

    public Dtspb5112SetExpiryRollbackMigrationHandler(
        ElasticSearchHandler elasticSearchHandler,
        Dtspb5112Config config,
        Dtspb5112ElasticQueries queries,
        Dtspb5112PaymentService paymentService,
        Dtspb5112MigrationSupport support,
        Dtspb5112RollbackSupport rollbackSupport
    ) {
        this.elasticSearchHandler = elasticSearchHandler;
        this.config = config;
        this.queries = queries;
        this.paymentService = paymentService;
        this.support = support;
        this.rollbackSupport = rollbackSupport;
    }

    @Override
    public Set<CaseSummary> getCandidateCases(UserToken userToken, S2sToken s2sToken) {
        return elasticSearchHandler.searchCases(
            Dtspb5112Constants.S2_ROLLBACK_ID,
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            (pageSize, from) -> queries.withExpiryModifiedSince(
                pageSize,
                Dtspb5112Constants.LIVE_STATES,
                config.getRollbackDate(),
                from)
        );
    }

    @Override
    public MigrationEvent startEventForCase(CaseSummary caseSummary, UserToken userToken, S2sToken s2sToken) {
        return support.start(caseSummary, userToken, s2sToken, ROLLBACK_EVENT);
    }

    @Override
    public boolean shouldMigrateCase(MigrationEvent migrationEvent) {
        LocalDate paymentDate = paymentService.successfulPaymentDate(migrationEvent).orElse(null);
        Object expiry = support.requireCaseDetails(migrationEvent).getData().get(Dtspb5112Constants.EXPIRY_DATE);

        return paymentDate != null
            && expiry != null
            && paymentDate.plusMonths(6).toString().equals(expiry.toString())
            && rollbackSupport.hasMigrationEvent(
                migrationEvent,
                Dtspb5112SetExpiryMigrationHandler.DESCRIPTION
            );
    }

    @Override
    public boolean migrate(MigrationEvent migrationEvent) {
        Map<String, Object> data = support.mutableData(migrationEvent);
        data.put(Dtspb5112Constants.EXPIRY_DATE, null);
        support.addMigrationCallbackMetadata(data, Dtspb5112Constants.S2_ROLLBACK_ID);

        return support.submit(migrationEvent, data, SUMMARY, DESCRIPTION);
    }
}
