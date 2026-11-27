package com.walletpay.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonProperty;

public record BankWebhookRequest(
        @NotBlank String token,
        @NotBlank @JsonProperty("user_identifier") String userIdentifier,
        @Positive long amount) {}
