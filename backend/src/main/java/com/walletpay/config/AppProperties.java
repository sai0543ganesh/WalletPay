package com.walletpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String jwtSecret,
        long jwtTtlMinutes,
        boolean cookieSecure,
        String allowedOrigins,
        boolean sandboxWebhookEnabled,
        String webhookSecret,
        boolean demoFundingEnabled) {}
