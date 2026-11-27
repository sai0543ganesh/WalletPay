package com.walletpay.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public API values. Monetary amounts are always integer paise. */
public final class ApiModels {
    private ApiModels() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 80) String fullName,
            @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
            @NotBlank @Size(min = 12, max = 128) String password) {}

    public record LoginRequest(
            @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
            @NotBlank @Size(max = 128) String password) {}

    public record ProfileRequest(@NotBlank @Size(min = 2, max = 80) String fullName) {}

    public record PasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}

    public record TransferRequest(
            @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String recipientPhone,
            @Min(1) @Max(100_000_000) long amountMinor,
            @Size(max = 160) String note) {}

    public record DepositRequest(
            @Min(1) @Max(100_000_000) long amountMinor,
            @NotNull @Pattern(regexp = "SANDBOX") String provider) {}

    public record WebhookRequest(
            @NotNull UUID eventId,
            @NotNull UUID depositId,
            @Min(1) @Max(100_000_000) long amountMinor,
            @NotNull @Pattern(regexp = "INR") String currency,
            @NotNull @Pattern(regexp = "COMPLETED") String status) {}

    public record UserResponse(Long id, String fullName, String phone) {}

    public record BalanceResponse(long balanceMinor, String currency, Instant updatedAt) {}

    public record DemoFundsResponse(
            boolean enabled, boolean welcomeAvailable, long welcomeCreditMinor) {}

    public record TransactionResponse(
            UUID id,
            String reference,
            String type,
            String direction,
            String status,
            long amountMinor,
            String currency,
            String note,
            String counterpartyName,
            String counterpartyPhone,
            Instant createdAt) {}

    public record PageResponse<T>(
            List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record DailySummary(LocalDate date, long incomeMinor, long spendingMinor) {}

    public record AnalyticsResponse(
            int periodDays,
            long incomeMinor,
            long spendingMinor,
            long transferCount,
            List<DailySummary> daily) {}
}
