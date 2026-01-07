package uk.gov.hmcts.reform.migration.reimpl.dto;

import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.TokenResponse;

public record UserToken(TokenResponse tokenResponse) {
    public String getBearerToken() {
        return IdamClient.BEARER_AUTH_TYPE + " " + tokenResponse.accessToken;
    }
}
