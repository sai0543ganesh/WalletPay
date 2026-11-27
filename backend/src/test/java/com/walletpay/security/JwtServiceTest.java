package com.walletpay.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.walletpay.config.AppProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtServiceTest {
    private AppProperties properties(String key) {
        return new AppProperties(key, 15, false, "http://localhost:5173", false, "", false);
    }

    @Test
    void signsAndValidatesIdentity() {
        JwtService jwt =
                new JwtService(
                        properties("unit-test-signing-key-0123456789-abcdef"),
                        new MockEnvironment());
        UUID session = UUID.randomUUID();
        String token = jwt.issue(7L, session, Instant.now().plusSeconds(60));
        assertThat(jwt.verify(token)).isEqualTo(new JwtService.Identity(7L, session));
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwt =
                new JwtService(
                        properties("unit-test-signing-key-0123456789-abcdef"),
                        new MockEnvironment());
        String token = jwt.issue(7L, UUID.randomUUID(), Instant.now().minusSeconds(60));
        assertThatThrownBy(() -> jwt.verify(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void rejectsDifferentSigningKey() {
        JwtService a =
                new JwtService(
                        properties("unit-test-signing-key-0123456789-abcdef"),
                        new MockEnvironment());
        JwtService b =
                new JwtService(
                        properties("different-signing-key-0123456789-abcdef"),
                        new MockEnvironment());
        String token = a.issue(7L, UUID.randomUUID(), Instant.now().plusSeconds(60));
        assertThatThrownBy(() -> b.verify(token)).isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void productionFailsClosedWithoutSecret() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtService(properties(""), prod))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsShortConfiguredSecret() {
        assertThatThrownBy(() -> new JwtService(properties("short"), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }
}
