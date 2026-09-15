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

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112.Dtspb5112Constants.MIGRATION_EVENT;

@Component
public class Dtspb5112CloseNewlyExpiredMigrationHandler implements MigrationHandler {
    static final String SUMMARY = "DTSPB-5112 - Close newly expired caveats";
    static final String DESCRIPTION = "Close caveats whose expiry was set by DTSPB-5112 and is already in the past";

    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb5112ElasticQueries queries;
    private final Dtspb5112MigrationSupport support;
    private final Dtspb5112RollbackSupport rollbackSupport;
    private final Dtspb5112Config config;
    private final Clock clock;

    public Dtspb5112CloseNewlyExpiredMigrationHandler(
        ElasticSearchHandler elasticSearchHandler,
        Dtspb5112ElasticQueries queries,
        Dtspb5112MigrationSupport support,
        Dtspb5112RollbackSupport rollbackSupport,
        Dtspb5112Config config,
        Clock clock
    ) {
        this.elasticSearchHandler = elasticSearchHandler;
        this.queries = queries;
        this.support = support;
        this.rollbackSupport = rollbackSupport;
        this.config = config;
        this.clock = clock;
    }

    @Override
    public Set<CaseSummary> getCandidateCases(UserToken userToken, S2sToken s2sToken) {
        LocalDate today = LocalDate.now(clock);
        return elasticSearchHandler.searchCases(
            Dtspb5112Constants.S3_ID,
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            (pageSize, from) -> queries.expiredLastModifiedSince(
                pageSize,
                Dtspb5112Constants.LIVE_STATES,
                today,
                config.getRollbackDate(),
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
        Object expiry = details.getData().get(Dtspb5112Constants.EXPIRY_DATE);

        return Dtspb5112Constants.LIVE_STATES.contains(details.getState())
            && expiry != null
            && LocalDate.parse(expiry.toString()).isBefore(LocalDate.now(clock))
            && rollbackSupport.hasMigrationEvent(
                migrationEvent,
                Dtspb5112SetExpiryMigrationHandler.DESCRIPTION
            );
    }

    @Override
    public boolean migrate(MigrationEvent migrationEvent) {
        Map<String, Object> data = support.mutableData(migrationEvent);
        support.addMigrationCallbackMetadata(data, Dtspb5112Constants.S3_ID);
        return support.submit(migrationEvent, data, SUMMARY, DESCRIPTION);
    }
}
