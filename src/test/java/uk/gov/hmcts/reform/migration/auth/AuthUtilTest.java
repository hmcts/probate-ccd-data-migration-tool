package uk.gov.hmcts.reform.migration.auth;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class AuthUtilTest {

    @Test
    void shouldGetBearToken() {
        assertThat(AuthUtil.getBearerToken("aaaa"), is("Bearer aaaa"));
    }

    @Test
    void shouldReturnGetBearToken() {
        assertThat(AuthUtil.getBearerToken("Bearer aaaa"), is("Bearer aaaa"));
    }

    @Test
    void shouldReturnBlankToken() {
        assertThat(AuthUtil.getBearerToken(""), is(""));
    }
}
