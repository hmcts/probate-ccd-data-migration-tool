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
public class Dtspb5112CloseExistingExpiredMigrationHandler implements MigrationHandler {
    static final String SUMMARY = "DTSPB-5112 - Close existing expired caveats";
    static final String DESCRIPTION = "Close existing live caveats whose expiry date has passed";

    private final ElasticSearchHandler elasticSearchHandler;
    private final Dtspb5112ElasticQueries queries;
    private final Dtspb5112MigrationSupport support;
    private final Clock clock;

    public Dtspb5112CloseExistingExpiredMigrationHandler(
        ElasticSearchHandler elasticSearchHandler,
        Dtspb5112ElasticQueries queries,
        Dtspb5112MigrationSupport support,
        Clock clock
    ) {
        this.elasticSearchHandler = elasticSearchHandler;
        this.queries = queries;
        this.support = support;
        this.clock = clock;
    }

    @Override
    public Set<CaseSummary> getCandidateCases(UserToken userToken, S2sToken s2sToken) {
        LocalDate today = LocalDate.now(clock);
        return elasticSearchHandler.searchCases(
            Dtspb5112Constants.S4_ID,
            userToken,
            s2sToken,
            CaseType.CAVEAT,
            (pageSize, from) -> queries.expired(pageSize, Dtspb5112Constants.LIVE_STATES, today, from)
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
            && LocalDate.parse(expiry.toString()).isBefore(LocalDate.now(clock));
    }

    @Override
    public boolean migrate(MigrationEvent migrationEvent) {
        Map<String, Object> data = support.mutableData(migrationEvent);
        support.addMigrationCallbackMetadata(data, Dtspb5112Constants.S4_ID);
        return support.submit(migrationEvent, data, SUMMARY, DESCRIPTION);
    }
}
