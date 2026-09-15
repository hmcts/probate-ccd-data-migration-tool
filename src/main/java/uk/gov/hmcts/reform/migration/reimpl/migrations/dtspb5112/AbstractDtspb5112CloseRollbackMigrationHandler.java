package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.Map;
import java.util.Set;

import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112.Dtspb5112Constants.ROLLBACK_EVENT;

abstract class AbstractDtspb5112CloseRollbackMigrationHandler implements MigrationHandler {
    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb5112ElasticQueries queries;
    protected final Dtspb5112MigrationSupport support;
    private final Dtspb5112RollbackSupport rollbackSupport;

    AbstractDtspb5112CloseRollbackMigrationHandler(
        ElasticSearchHandler elasticSearchHandler,
        Dtspb5112ElasticQueries queries,
        Dtspb5112MigrationSupport support,
        Dtspb5112RollbackSupport rollbackSupport
    ) {
        this.elasticSearchHandler = elasticSearchHandler;
        this.queries = queries;
        this.support = support;
        this.rollbackSupport = rollbackSupport;
    }

    abstract String rollbackId();

    abstract String migrationDescription();

    abstract String rollbackSummary();

    abstract String rollbackDescription();

    @Override
    public Set<CaseSummary> getCandidateCases(UserToken userToken, S2sToken s2sToken) {
        return elasticSearchHandler.searchCases(
            rollbackId(),
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            (pageSize, from) -> queries.stateOnly(
                pageSize,
                Set.of(Dtspb5112Constants.CAVEAT_CLOSED),
                from
            )
        );
    }

    @Override
    public MigrationEvent startEventForCase(CaseSummary caseSummary, UserToken userToken, S2sToken s2sToken) {
        return support.start(caseSummary, userToken, s2sToken, ROLLBACK_EVENT);
    }

    @Override
    public boolean shouldMigrateCase(MigrationEvent migrationEvent) {
        return Dtspb5112Constants.CAVEAT_CLOSED.equals(support.requireCaseDetails(migrationEvent).getState())
            && rollbackSupport.hasMigrationEvent(migrationEvent, migrationDescription());
    }

    @Override
    public boolean migrate(MigrationEvent migrationEvent) {
        Map<String, Object> data = support.mutableData(migrationEvent);
        support.addMigrationCallbackMetadata(data, rollbackId());
        return support.submit(migrationEvent, data, rollbackSummary(), rollbackDescription());
    }
}
