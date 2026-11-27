package com.walletpay.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiRateLimitFilterTest {
    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsUnknownLengthOversizedBodies() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/deposits");
        request.setContent(new byte[32_769]);
        var unknownLength =
                new HttpServletRequestWrapper(request) {
                    @Override
                    public long getContentLengthLong() {
                        return -1;
                    }

                    @Override
                    public int getContentLength() {
                        return -1;
                    }
                };
        var response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        new ApiRateLimitFilter(new WindowRateLimiter())
                .doFilter(unknownLength, response, (req, res) -> invoked.set(true));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(invoked).isFalse();
    }

    @Test
    void preservesValidBodyAndIgnoresForwardedIpSpoofing() throws Exception {
        var limiter = new ApiRateLimitFilter(new WindowRateLimiter());
        for (int i = 0; i < 301; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            request.setContent("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();
            limiter.doFilter(
                    request,
                    response,
                    (req, res) ->
                            assertThat(
                                            new String(
                                                    req.getInputStream().readAllBytes(),
                                                    StandardCharsets.UTF_8))
                                    .isEqualTo("{\"ok\":true}"));
            assertThat(response.getStatus()).isEqualTo(i < 300 ? 200 : 429);
        }
    }

    @Test
    void authenticatedUsersHaveIndependentBudgetsBehindOneProxy() throws Exception {
        var limiter = new ApiRateLimitFilter(new WindowRateLimiter());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        for (int i = 0; i < 300; i++)
            limiter.doFilter(
                    new MockHttpServletRequest("GET", "/api/v1/wallet"),
                    new MockHttpServletResponse(),
                    (req, res) -> {});
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(2L, null, List.of()));
        var response = new MockHttpServletResponse();
        limiter.doFilter(
                new MockHttpServletRequest("GET", "/api/v1/wallet"), response, (req, res) -> {});
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
