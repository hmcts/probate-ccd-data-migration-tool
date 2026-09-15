package uk.gov.hmcts.reform.migration.reimpl.migrations.dtspb5112;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.migration.reimpl.service.ElasticSearchHandler;

@Component
public class Dtspb5112CloseNewlyExpiredRollbackMigrationHandler
    extends AbstractDtspb5112CloseRollbackMigrationHandler {

    public Dtspb5112CloseNewlyExpiredRollbackMigrationHandler(
        ElasticSearchHandler elasticSearchHandler,
        Dtspb5112ElasticQueries queries,
        Dtspb5112MigrationSupport support,
        Dtspb5112RollbackSupport rollbackSupport
    ) {
        super(elasticSearchHandler, queries, support, rollbackSupport);
    }

    @Override
    String rollbackId() {
        return Dtspb5112Constants.S3_ROLLBACK_ID;
    }

    @Override
    String migrationDescription() {
        return Dtspb5112CloseNewlyExpiredMigrationHandler.DESCRIPTION;
    }

    @Override
    String rollbackSummary() {
        return "DTSPB-5112 - Rollback newly expired caveat closure";
    }

    @Override
    String rollbackDescription() {
        return "Rollback DTSPB-5112 newly expired caveat closure";
    }
}
