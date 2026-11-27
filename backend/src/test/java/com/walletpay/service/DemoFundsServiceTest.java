package com.walletpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.walletpay.config.AppProperties;
import com.walletpay.entity.Wallet;
import com.walletpay.entity.WalletTransaction;
import com.walletpay.exception.ApiException;
import com.walletpay.repository.TransactionRepository;
import com.walletpay.repository.WalletRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoFundsServiceTest {
    @Mock WalletRepository wallets;
    @Mock TransactionRepository transactions;
    @Mock WalletService walletService;
    @Mock AuditService audit;
    DemoFundsService service;

    private DemoFundsService configured(boolean enabled) {
        return new DemoFundsService(
                new AppProperties("", 15, false, "http://localhost:5173", false, "", enabled),
                wallets,
                transactions,
                walletService,
                audit);
    }

    @BeforeEach
    void setup() {
        service = configured(true);
    }

    @Test
    void disabledGrantDoesNotTouchStorage() {
        assertThatThrownBy(() -> configured(false).claimWelcome(1L, UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(wallets, transactions, audit, walletService);
    }

    @Test
    void disabledStatusDoesNotAdvertiseGrant() {
        when(wallets.findById(1L)).thenReturn(Optional.of(new Wallet(1L)));
        var status = configured(false).status(1L);
        assertThat(status.enabled()).isFalse();
        assertThat(status.welcomeAvailable()).isFalse();
        assertThat(status.welcomeCreditMinor()).isEqualTo(10_000);
    }

    @Test
    void completedDemoTransactionHasCompletionTimeButDepositDoesNot() {
        var demo =
                new WalletTransaction(
                        "DEMO_CREDIT", 1L, 1L, 10_000, "Demo", UUID.randomUUID(), "hash");
        var deposit =
                new WalletTransaction(
                        "DEPOSIT", 1L, 1L, 10_000, "Deposit", UUID.randomUUID(), "hash");
        assertThat(demo.getStatus()).isEqualTo("COMPLETED");
        assertThat(demo.getCompletedAt()).isEqualTo(demo.getCreatedAt());
        assertThat(deposit.getStatus()).isEqualTo("PENDING");
        assertThat(deposit.getCompletedAt()).isNull();
    }

    @Test
    void claimedAccountCannotCreateAnotherGrant() {
        Wallet wallet = new Wallet(1L);
        wallet.markDemoWelcomeClaimed();
        when(wallets.lockById(1L)).thenReturn(Optional.of(wallet));
        assertThatThrownBy(() -> service.claimWelcome(1L, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already received");
        verify(transactions, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void balanceLimitDoesNotConsumeEligibility() {
        Wallet wallet = new Wallet(1L);
        wallet.setBalanceMinor(Money.MAX_BALANCE);
        when(wallets.lockById(1L)).thenReturn(Optional.of(wallet));
        assertThatThrownBy(() -> service.claimWelcome(1L, UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        assertThat(wallet.isDemoWelcomeClaimed()).isFalse();
        assertThat(wallet.getBalanceMinor()).isEqualTo(Money.MAX_BALANCE);
        verify(transactions, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void grantsOnlyFixedAmountAndRecordsAudit() {
        Wallet wallet = new Wallet(1L);
        wallet.setBalanceMinor(50);
        when(wallets.lockById(1L)).thenReturn(Optional.of(wallet));
        when(transactions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service.claimWelcome(1L, UUID.randomUUID());
        assertThat(wallet.getBalanceMinor()).isEqualTo(10_050);
        assertThat(wallet.isDemoWelcomeClaimed()).isTrue();
        verify(audit)
                .record(
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq("DEMO_WELCOME_CLAIMED"),
                        any());
    }
}
