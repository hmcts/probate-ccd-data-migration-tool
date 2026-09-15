package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.springframework.stereotype.Component;
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

@Component
public class Dtspb5112PaAppCreatedRollbackMigrationHandler implements MigrationHandler {
    static final String SUMMARY = "DTSPB-5112 - Rollback PA caveat state migration";
    static final String DESCRIPTION = "Rollback DTSPB-5112 PAAppCreated to CaveatRaised migration";

    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb5112Config config;
    private final Dtspb5112ElasticQueries queries;
    private final Dtspb5112MigrationSupport support;
    private final Dtspb5112RollbackSupport rollbackSupport;

    public Dtspb5112PaAppCreatedRollbackMigrationHandler(
        ElasticSearchHandler elasticSearchHandler,
        Dtspb5112Config config,
        Dtspb5112ElasticQueries queries,
        Dtspb5112MigrationSupport support,
        Dtspb5112RollbackSupport rollbackSupport
    ) {
        this.elasticSearchHandler = elasticSearchHandler;
        this.config = config;
        this.queries = queries;
        this.support = support;
        this.rollbackSupport = rollbackSupport;
    }

    @Override
    public Set<CaseSummary> getCandidateCases(UserToken userToken, S2sToken s2sToken) {
        return elasticSearchHandler.searchCases(
            Dtspb5112Constants.S1_ROLLBACK_ID,
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            (pageSize, from) -> queries.stateOnlyModifiedSince(
                pageSize,
                Set.of(Dtspb5112Constants.CAVEAT_RAISED),
                config.getRollbackDate(),
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
        return Dtspb5112Constants.CAVEAT_RAISED.equals(support.requireCaseDetails(migrationEvent).getState())
            && rollbackSupport.hasMigrationEvent(
                migrationEvent,
                Dtspb5112PaAppCreatedMigrationHandler.DESCRIPTION
            );
    }

    @Override
    public boolean migrate(MigrationEvent migrationEvent) {
        Map<String, Object> data = support.mutableData(migrationEvent);
        support.addMigrationCallbackMetadata(data, Dtspb5112Constants.S1_ROLLBACK_ID);
        return support.submit(migrationEvent, data, SUMMARY, DESCRIPTION);
    }
}
