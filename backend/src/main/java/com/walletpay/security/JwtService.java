package com.walletpay.security;

import com.walletpay.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long lifetime;

    public record Identity(Long userId, UUID sessionId) {}

    public JwtService(AppProperties properties, Environment environment) {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        String secret = properties.jwtSecret();
        if (secret == null || secret.isBlank()) {
            if (production)
                throw new IllegalStateException("JWT_SECRET is required in production.");
            byte[] bytes = new byte[48];
            new SecureRandom().nextBytes(bytes);
            key = Keys.hmacShaKeyFor(bytes);
            LoggerFactory.getLogger(JwtService.class)
                    .warn(
                            "Using an ephemeral development JWT key; sessions will expire on"
                                    + " restart.");
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32)
                throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes.");
            key = Keys.hmacShaKeyFor(bytes);
        }
        lifetime = properties.jwtTtlMinutes();
        if (lifetime < 1 || lifetime > 60)
            throw new IllegalStateException("JWT_TTL_MINUTES must be between 1 and 60.");
        if (production
                && (!properties.cookieSecure()
                        || properties.allowedOrigins() == null
                        || properties.allowedOrigins().isBlank()
                        || Arrays.stream(properties.allowedOrigins().split(","))
                                .anyMatch(
                                        origin ->
                                                !origin.strip().startsWith("https://")
                                                        || origin.contains("*")))) {
            throw new IllegalStateException(
                    "Production requires secure cookies and explicit HTTPS ALLOWED_ORIGINS.");
        }
        if (properties.sandboxWebhookEnabled()
                && (properties.webhookSecret() == null
                        || properties.webhookSecret().getBytes(StandardCharsets.UTF_8).length
                                < 32)) {
            throw new IllegalStateException(
                    "Enabled sandbox webhooks require WEBHOOK_SECRET with at least 32 UTF-8"
                            + " bytes.");
        }
    }

    public Instant expiresAt() {
        return Instant.now().plusSeconds(lifetime * 60);
    }

    public String issue(Long userId, UUID sessionId, Instant expiration) {
        return Jwts.builder()
                .issuer("walletpay")
                .subject(userId.toString())
                .id(sessionId.toString())
                .audience()
                .add("walletpay-browser")
                .and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    public Identity verify(String token) {
        Claims claims =
                Jwts.parser()
                        .verifyWith(key)
                        .requireIssuer("walletpay")
                        .requireAudience("walletpay-browser")
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
        return new Identity(Long.valueOf(claims.getSubject()), UUID.fromString(claims.getId()));
    }
}
