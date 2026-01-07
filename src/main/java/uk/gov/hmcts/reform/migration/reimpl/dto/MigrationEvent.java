package uk.gov.hmcts.reform.migration.reimpl.dto;

import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;

public record MigrationEvent(
    CaseSummary caseSummary,
    StartEventResponse startEventResponse,
    UserToken userToken,
    S2sToken s2sToken) {
}
