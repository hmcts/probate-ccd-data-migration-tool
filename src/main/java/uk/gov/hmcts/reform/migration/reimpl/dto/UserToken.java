package uk.gov.hmcts.reform.migration.reimpl.dto;

import org.json.JSONObject;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.idam.client.models.TokenResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public record UserToken(TokenResponse tokenResponse, UserDetails userDetails) {
    public String getBearerToken() {
        return IdamClient.BEARER_AUTH_TYPE + " " + tokenResponse.accessToken;
    }

    public Instant getExpiryTime() {
        final String[] components = tokenResponse.accessToken.split("\\.");

        if (components.length != 3) {
            throw new IllegalStateException("Expected 3 components, got " + components.length);
        }

        final byte[] decoded = Base64.getDecoder().decode(components[1]);
        final String decodedStr = new String(decoded, StandardCharsets.UTF_8);
        final JSONObject decodedJson = new JSONObject(decodedStr);
        final long exp = decodedJson.getLong("exp");
        return Instant.ofEpochSecond(exp);
    }
}
