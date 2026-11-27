package com.walletpay.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final WindowRateLimiter limiter;

    public ApiRateLimitFilter(WindowRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > 32_768) {
            ProblemResponses.write(
                    request,
                    response,
                    413,
                    "Payload Too Large",
                    "PAYLOAD_TOO_LARGE",
                    "Request body exceeds 32 KiB.");
            return;
        }
        if (request.getRequestURI().startsWith("/api/v1/")) {
            boolean auth =
                    request.getMethod().equals("POST")
                            && (request.getRequestURI().endsWith("/auth/login")
                                    || request.getRequestURI().endsWith("/auth/register"));
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            String identity =
                    authentication != null && authentication.getPrincipal() instanceof Long
                            ? "user:" + authentication.getPrincipal()
                            : "gateway:" + request.getRemoteAddr();
            String key = (auth ? "auth:" : "api:") + identity;
            // The remote IP is an aggregate guard behind a proxy. Per-phone login protection is in
            // AuthService.
            if (!limiter.allow(key, 300, Duration.ofMinutes(1))) {
                response.setHeader("Retry-After", "60");
                ProblemResponses.write(
                        request,
                        response,
                        429,
                        "Too Many Requests",
                        "RATE_LIMITED",
                        "Too many requests. Please try again later.");
                return;
            }
        }
        if (request.getRequestURI().startsWith("/api/")
                && !request.getMethod().equals("GET")
                && !request.getMethod().equals("OPTIONS")) {
            byte[] body = request.getInputStream().readNBytes(32_769);
            if (body.length > 32_768) {
                ProblemResponses.write(
                        request,
                        response,
                        413,
                        "Payload Too Large",
                        "PAYLOAD_TOO_LARGE",
                        "Request body exceeds 32 KiB.");
                return;
            }
            chain.doFilter(new BufferedRequest(request, body), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] bytes;

        BufferedRequest(HttpServletRequest request, byte[] bytes) {
            super(request);
            this.bytes = bytes;
        }

        @Override
        public int getContentLength() {
            return bytes.length;
        }

        @Override
        public long getContentLengthLong() {
            return bytes.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException(
                            "Asynchronous request bodies are not supported.");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
