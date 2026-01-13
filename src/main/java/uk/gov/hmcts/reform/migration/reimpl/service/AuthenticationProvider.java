package uk.gov.hmcts.reform.migration.reimpl.service;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.migration.reimpl.config.ReimplConfig;
import uk.gov.hmcts.reform.migration.reimpl.dto.S2sToken;
import uk.gov.hmcts.reform.migration.reimpl.dto.UserToken;
import uk.gov.hmcts.reform.migration.repository.IdamRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class AuthenticationProvider {
    private final IdamRepository idamRepository;
    private final AuthTokenGenerator authTokenGenerator;

    private final ReimplConfig reimplConfig;
    private final Clock clock;

    private final Object userTokenLock;
    private UserToken userToken;

    private final Object s2sTokenLock;
    private S2sToken s2sToken;

    public AuthenticationProvider(
            final IdamRepository idamRepository,
            final AuthTokenGenerator authTokenGenerator,
            final ReimplConfig reimplConfig,
            final Clock clock) {
        this.idamRepository = Objects.requireNonNull(idamRepository);
        this.authTokenGenerator = Objects.requireNonNull(authTokenGenerator);

        this.reimplConfig = Objects.requireNonNull(reimplConfig);
        this.clock = Objects.requireNonNull(clock);

        this.userTokenLock = new Object();
        this.userToken = null;

        this.s2sTokenLock = new Object();
        this.s2sToken = null;
    }

    public UserToken getUserToken() {
        synchronized (userTokenLock) {
            if (userToken == null) {
                userToken = idamRepository.generateUserTokenObject();
            } else {
                final Instant now = Instant.now(clock);
                final Instant checkAfter = now.minus(reimplConfig.getUserTokenRefreshMargin());

                final Instant userExpiryTime = userToken.getExpiryTime();

                if (userExpiryTime.isAfter(checkAfter)) {
                    userToken = idamRepository.generateUserTokenObject();
                }
            }
            return userToken;
        }
    }

    public S2sToken getS2sToken() {
        synchronized (s2sTokenLock) {
            if (s2sToken == null) {
                s2sToken = new S2sToken(authTokenGenerator.generate());
            } else {
                final Instant now = Instant.now(clock);
                final Instant checkAfter = now.minus(reimplConfig.getS2sTokenRefreshMargin());

                final Instant userExpiryTime = s2sToken.getExpiryTime();

                if (userExpiryTime.isAfter(checkAfter)) {
                    s2sToken = new S2sToken(authTokenGenerator.generate());
                }
            }
            return s2sToken;
        }
    }
}
