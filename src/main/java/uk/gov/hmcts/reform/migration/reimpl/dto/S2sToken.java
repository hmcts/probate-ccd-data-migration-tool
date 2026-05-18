package uk.gov.hmcts.reform.migration.reimpl.dto;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public record S2sToken(String s2sToken) {

    public Instant getExpiryTime() {
        final String[] components = s2sToken.split("\\.");

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
