package com.walletpay.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WindowRateLimiterTest {
    @Test
    void blocksAfterLimitAndSeparatesKeys() {
        WindowRateLimiter limiter = new WindowRateLimiter();
        assertThat(limiter.allow("a", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.allow("a", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.allow("a", 2, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.allow("b", 2, Duration.ofMinutes(1))).isTrue();
        limiter.clear("a");
        assertThat(limiter.allow("a", 2, Duration.ofMinutes(1))).isTrue();
    }
}
