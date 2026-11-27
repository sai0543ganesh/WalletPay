package com.walletpay.service;

import com.walletpay.config.AppProperties;
import com.walletpay.dto.ApiModels.DemoFundsResponse;
import com.walletpay.dto.ApiModels.TransactionResponse;
import com.walletpay.entity.Wallet;
import com.walletpay.entity.WalletTransaction;
import com.walletpay.exception.ApiException;
import com.walletpay.repository.TransactionRepository;
import com.walletpay.repository.WalletRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicitly simulated funds. This service never requests or moves real money. */
@Service
public class DemoFundsService {
    public static final long WELCOME_CREDIT_MINOR = 10_000;
    private static final String REQUEST_HASH = Digests.sha256("DEMO_WELCOME\n10000\nINR");

    private final AppProperties properties;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final WalletService walletService;
    private final AuditService audit;

    public DemoFundsService(
            AppProperties properties,
            WalletRepository wallets,
            TransactionRepository transactions,
            WalletService walletService,
            AuditService audit) {
        this.properties = properties;
        this.wallets = wallets;
        this.transactions = transactions;
        this.walletService = walletService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public DemoFundsResponse status(Long userId) {
        Wallet wallet = wallets.findById(userId).orElseThrow(ApiException::notFound);
        boolean enabled = properties.demoFundingEnabled();
        return new DemoFundsResponse(
                enabled, enabled && !wallet.isDemoWelcomeClaimed(), WELCOME_CREDIT_MINOR);
    }

    @Transactional
    public TransactionResponse claimWelcome(Long userId, UUID key) {
        if (!properties.demoFundingEnabled()) throw ApiException.notFound();
        Wallet wallet = wallets.lockById(userId).orElseThrow(ApiException::notFound);
        // Replay is checked before eligibility so a completed claim remains safely retryable.
        WalletTransaction existing =
                transactions.findBySenderIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing != null) {
            if (!REQUEST_HASH.equals(existing.getRequestHash())) {
                throw ApiException.conflict(
                        "IDEMPOTENCY_CONFLICT",
                        "This idempotency key was already used for a different request.");
            }
            return walletService.view(existing, userId);
        }
        if (wallet.isDemoWelcomeClaimed()) {
            throw ApiException.conflict(
                    "WELCOME_ALREADY_CLAIMED",
                    "This account has already received its one-time demo credit.");
        }
        wallet.setBalanceMinor(Money.credit(wallet.getBalanceMinor(), WELCOME_CREDIT_MINOR));
        wallet.markDemoWelcomeClaimed();
        WalletTransaction transaction =
                transactions.saveAndFlush(
                        new WalletTransaction(
                                "DEMO_CREDIT",
                                userId,
                                userId,
                                WELCOME_CREDIT_MINOR,
                                "One-time simulated welcome credit",
                                key,
                                REQUEST_HASH));
        audit.record(userId, "DEMO_WELCOME_CLAIMED", transaction.getReference());
        return walletService.view(transaction, userId);
    }
}
