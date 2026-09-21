package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseType;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;
import uk.gov.hmcts.reform.migration.reimpl.service.MigrationHandler;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112.Dtspb5112Constants.ROLLBACK_EVENT;

abstract class AbstractDtspb5112CloseRollbackMigrationHandler implements MigrationHandler {
    private final Dtspb5112Config config;
    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb5112ElasticQueries queries;
    protected final Dtspb5112MigrationSupport support;
    private final Dtspb5112RollbackSupport rollbackSupport;
    private final Map<Long, String> originalStates = new ConcurrentHashMap<>();

    AbstractDtspb5112CloseRollbackMigrationHandler(
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
            (pageSize, from) -> queries.stateOnlyModifiedSince(
                pageSize,
                Set.of(Dtspb5112Constants.CAVEAT_CLOSED),
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
        if (!Dtspb5112Constants.CAVEAT_CLOSED.equals(
            support.requireCaseDetails(migrationEvent).getState()
        )) {
            return false;
        }

        Optional<String> originalState =
            rollbackSupport.findOriginalStateForMigration(
                    migrationEvent,
                    migrationDescription()
                )
                .filter(Dtspb5112Constants.LIVE_STATES::contains);

        originalState.ifPresent(state ->
            originalStates.put(
                migrationEvent.caseSummary().reference(),
                state
            )
        );

        return originalState.isPresent();
    }

    @Override
    public boolean migrate(MigrationEvent migrationEvent) {
        Long reference = migrationEvent.caseSummary().reference();

        String originalState = originalStates.remove(reference);

        if (originalState == null) {
            originalState = rollbackSupport
                .findOriginalStateForMigration(
                    migrationEvent,
                    migrationDescription()
                )
                .filter(Dtspb5112Constants.LIVE_STATES::contains)
                .orElseThrow(() -> new IllegalStateException(
                    "Unable to determine original state for case " + reference
                ));
        }
        Map<String, Object> data = support.mutableData(migrationEvent);
        support.addMigrationCallbackMetadata(data, rollbackId(), originalState);
        return support.submit(migrationEvent, data, rollbackSummary(), rollbackDescription());
    }
}
