package uk.gov.hmcts.reform.migration.reimpl.service;

import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.MigrationEvent;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.util.Set;

public interface MigrationHandler {
    Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken);

    MigrationEvent startEventForCase(
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken)

    boolean shouldMigrateCase(final MigrationEvent migrationEvent);

    boolean migrate(final MigrationEvent migrationEvent);
}
