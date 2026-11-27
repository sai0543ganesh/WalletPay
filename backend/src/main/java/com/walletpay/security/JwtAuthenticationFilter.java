package com.walletpay.security;

import com.walletpay.repository.SessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String COOKIE = "walletpay_session";
    private final JwtService jwt;
    private final SessionRepository sessions;

    public JwtAuthenticationFilter(JwtService jwt, SessionRepository sessions) {
        this.jwt = jwt;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();
        if (cookies != null)
            for (Cookie cookie : cookies) {
                if (!COOKIE.equals(cookie.getName())) continue;
                try {
                    JwtService.Identity identity = jwt.verify(cookie.getValue());
                    if (sessions.existsByIdAndUserIdAndExpiresAtAfter(
                            identity.sessionId(), identity.userId(), Instant.now())) {
                        var auth =
                                new UsernamePasswordAuthenticationToken(
                                        identity.userId(), identity.sessionId(), List.of());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                    SecurityContextHolder.clearContext();
                }
                break;
            }
        chain.doFilter(request, response);
    }
}
