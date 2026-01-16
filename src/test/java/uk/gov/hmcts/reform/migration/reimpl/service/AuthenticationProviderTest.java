package uk.gov.hmcts.reform.migration.reimpl.service;

import com.nimbusds.jose.shaded.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.repository.IdamRepository;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationProviderTest {
    @Mock
    IdamRepository idamRepositoryMock;
    @Mock
    AuthTokenGenerator authTokenGeneratorMock;
    @Mock
    ReimplConfig reimplConfigMock;
    @Mock
    Clock clockMock;

    AutoCloseable closeableMocks;

    AuthenticationProvider authenticationProvider;

    @BeforeEach
    void setUp() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        authenticationProvider = new AuthenticationProvider(
                idamRepositoryMock,
                authTokenGeneratorMock,
                reimplConfigMock,
                clockMock);
    }

    @AfterEach
    void tearDown() {
        try {
            closeableMocks.close();
        } catch (Exception e) {
            // nothing to do
        }
    }

    @Test
    void testFirstGetUserTokenNoCheckTimeout() {
        authenticationProvider.getUserToken();

        verify(clockMock, never())
                .instant();
    }

    @Test
    void testSecondGetUserTokenChecksTimeout() {
        final Instant now = Instant.ofEpochSecond(1000);
        final Instant expiry = Instant.ofEpochSecond(2000);
        final Duration margin = Duration.ofSeconds(100);

        final UserToken userTokenMock = mock();
        when(idamRepositoryMock.generateUserTokenObject())
            .thenReturn(userTokenMock);
        when(userTokenMock.getExpiryTime())
            .thenReturn(expiry);

        when(clockMock.instant())
            .thenReturn(now);
        when(reimplConfigMock.getUserTokenRefreshMargin())
            .thenReturn(margin);

        authenticationProvider.getUserToken();
        authenticationProvider.getUserToken();

        verify(idamRepositoryMock, times(1))
            .generateUserTokenObject();
    }

    @Test
    void testSecondGetUserTokenRegeneratesIfTimedOut() {
        final Instant now = Instant.ofEpochSecond(1000);
        final Instant expiry = Instant.ofEpochSecond(1050);
        final Duration margin = Duration.ofSeconds(100);

        final UserToken userTokenMock = mock();
        when(idamRepositoryMock.generateUserTokenObject())
            .thenReturn(userTokenMock);
        when(userTokenMock.getExpiryTime())
            .thenReturn(expiry);

        when(clockMock.instant())
            .thenReturn(now);
        when(reimplConfigMock.getUserTokenRefreshMargin())
            .thenReturn(margin);

        authenticationProvider.getUserToken();
        authenticationProvider.getUserToken();

        verify(idamRepositoryMock, times(2))
            .generateUserTokenObject();
    }

    @Test
    void testFirstGetS2sTokenNoCheckTimeout() {
        authenticationProvider.getS2sToken();

        verify(clockMock, never())
                .instant();
    }

    @Test
    void testSecondGetS2sTokenChecksTimeout() {
        final Instant now = Instant.ofEpochSecond(1000);
        final String expiryJson = getS2SAuthTokenExpiringAt(2000);
        final Duration margin = Duration.ofSeconds(100);

        when(authTokenGeneratorMock.generate())
            .thenReturn(expiryJson);

        when(clockMock.instant())
            .thenReturn(now);
        when(reimplConfigMock.getS2sTokenRefreshMargin())
            .thenReturn(margin);

        authenticationProvider.getS2sToken();
        authenticationProvider.getS2sToken();

        verify(authTokenGeneratorMock, times(1))
            .generate();
    }

    @Test
    void testSecondGetS2STokenRegeneratesIfTimedOut() {
        final Instant now = Instant.ofEpochSecond(1000);
        final String expiryJson = getS2SAuthTokenExpiringAt(1050);
        final Duration margin = Duration.ofSeconds(100);

        when(authTokenGeneratorMock.generate())
            .thenReturn(expiryJson);

        when(clockMock.instant())
            .thenReturn(now);
        when(reimplConfigMock.getS2sTokenRefreshMargin())
            .thenReturn(margin);

        authenticationProvider.getS2sToken();
        authenticationProvider.getS2sToken();

        verify(authTokenGeneratorMock, times(2))
            .generate();
    }

    /**
     *  convenience method to generate valid enough s2s token objects for testing.
     */
    private static String getS2SAuthTokenExpiringAt(long expiry) {
        final JsonObject payload = new JsonObject();
        payload.addProperty("exp", expiry);
        final byte[] payloadBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        final String payloadB64 = Base64.getEncoder().encodeToString(payloadBytes);

        final String jwt = "a." + payloadB64 + ".a";
        return jwt;
    }
}
