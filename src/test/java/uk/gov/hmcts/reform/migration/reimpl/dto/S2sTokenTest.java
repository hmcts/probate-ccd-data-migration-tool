package uk.gov.hmcts.reform.migration.reimpl.dto;

import com.nimbusds.jose.shaded.gson.JsonObject;
import org.json.JSONException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

class S2sTokenTest {
    @Test
    void incorrectComponentsForJwtThrows() {
        final S2sToken empty = new S2sToken("");
        final S2sToken onePart = new S2sToken("aaa");
        final S2sToken twoPart = new S2sToken("aaa.aaa");
        final S2sToken expZero = base64Enc(Optional.of(0L));
        final S2sToken fourPart = new S2sToken("aaa.aaa.aaa.aaa");

        Assertions.assertAll(
            () -> Assertions.assertThrows(IllegalStateException.class, empty::getExpiryTime),
            () -> Assertions.assertThrows(IllegalStateException.class, onePart::getExpiryTime),
            () -> Assertions.assertThrows(IllegalStateException.class, twoPart::getExpiryTime),
            () -> Assertions.assertDoesNotThrow(expZero::getExpiryTime),
            () -> Assertions.assertThrows(IllegalStateException.class, fourPart::getExpiryTime));
    }

    @Test
    void invalidBase64PayloadThrows() {
        // '@' is not a valid base64 character
        final S2sToken nonBase64Payload = new S2sToken("a.@.a");

        Assertions.assertThrows(IllegalArgumentException.class, nonBase64Payload::getExpiryTime);
    }

    @Test
    void nonJsonPayloadThrows() {
        // 'aGVsbG8=' the string "hello" (i.e. not valid json)
        final S2sToken nonBase64Payload = new S2sToken("a.aGVsbG8=.a");

        Assertions.assertThrows(JSONException.class, nonBase64Payload::getExpiryTime);
    }

    @Test
    void jsonPayloadWithoutExpThrows() {
        final S2sToken noExp = base64Enc(Optional.empty());

        Assertions.assertThrows(JSONException.class, noExp::getExpiryTime);
    }

    @Test
    void jsonPayloadWithZeroExpIsEpoch() {
        final S2sToken negExp = base64Enc(Optional.of(0L));

        final Instant actual = negExp.getExpiryTime();

        assertThat(actual, equalTo(Instant.EPOCH));
    }

    @Test
    void jsonPayloadWithExpDecodesCorrectly() {
        final ZonedDateTime whenWritten = ZonedDateTime.of(2025, 1, 14, 16, 0, 0, 0, ZoneOffset.UTC);
        final Instant whenWrittenInstant = whenWritten.toInstant();

        final Long whenWrittenFromEpoch = 1736870400L;

        final S2sToken negExp = base64Enc(Optional.of(whenWrittenFromEpoch));

        final Instant actual = negExp.getExpiryTime();

        assertThat(actual, equalTo(whenWrittenInstant));
    }

    /**
     *  convenience method to generate valid enough s2s token objects for testing.
     */
    private static S2sToken base64Enc(Optional<Long> expiry) {
        final JsonObject payload = new JsonObject();
        if (expiry.isPresent()) {
            payload.addProperty("exp", expiry.get());
        }
        final byte[] payloadBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        final String payloadB64 = Base64.getEncoder().encodeToString(payloadBytes);

        final String jwt = "a." + payloadB64 + ".a";
        return new S2sToken(jwt);
    }
}
