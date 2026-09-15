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

import java.util.Map;
import java.util.Set;

import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112.Dtspb5112Constants.MIGRATION_EVENT;

@Component
public class Dtspb5112PaAppCreatedMigrationHandler implements MigrationHandler {
    static final String SUMMARY = "DTSPB-5112 - Move paid PA caveat applications to Caveat Raised";
    static final String DESCRIPTION = "Migrate PAAppCreated cases with successful payment to CaveatRaised";

    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb5112ElasticQueries queries;
    private final Dtspb5112PaymentService paymentService;
    private final Dtspb5112MigrationSupport support;

    public Dtspb5112PaAppCreatedMigrationHandler(
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
            Dtspb5112Constants.S1_ID,
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            (pageSize, from) -> queries.stateOnly(
                pageSize,
                Set.of(Dtspb5112Constants.PA_APP_CREATED),
                from
            )
        );
    }

    @Override
    public MigrationEvent startEventForCase(CaseSummary caseSummary, UserToken userToken, S2sToken s2sToken) {
        return support.start(caseSummary, userToken, s2sToken, MIGRATION_EVENT);
    }

    @Override
    public boolean shouldMigrateCase(MigrationEvent migrationEvent) {
        CaseDetails details = support.requireCaseDetails(migrationEvent);
        return Dtspb5112Constants.PA_APP_CREATED.equals(details.getState())
            && paymentService.hasSuccessfulPayment(migrationEvent);
    }

    @Override
    public boolean migrate(MigrationEvent migrationEvent) {
        Map<String, Object> data = support.mutableData(migrationEvent);
        support.addMigrationCallbackMetadata(data, Dtspb5112Constants.S1_ID);
        return support.submit(migrationEvent, data, SUMMARY, DESCRIPTION);
    }
}
