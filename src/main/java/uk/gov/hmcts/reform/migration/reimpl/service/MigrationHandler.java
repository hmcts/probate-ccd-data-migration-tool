package uk.gov.hmcts.reform.migration.reimpl.service;

import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.migration.reimpl.dto.CaseSummary;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;

import java.util.Set;

public interface MigrationHandler {
    Set<CaseSummary> getCandidateCases(
            final UserToken userToken,
            final S2sToken s2sToken);

    StartEventResponse startEventForCase(
            final CaseSummary caseSummary,
            final UserToken userToken,
            final S2sToken s2sToken);

    boolean shouldMigrateCase(
            final CaseSummary caseSummary,
            final StartEventResponse startEventResponse);

    boolean migrate(
            final CaseSummary caseSummary,
            final StartEventResponse startEventResponse,
            final UserToken userToken,
            final S2sToken s2sToken);
}
