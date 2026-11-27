package com.walletpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.walletpay.dto.ApiModels.TransferRequest;
import com.walletpay.entity.User;
import com.walletpay.entity.Wallet;
import com.walletpay.exception.ApiException;
import com.walletpay.repository.TransactionRepository;
import com.walletpay.repository.UserRepository;
import com.walletpay.repository.WalletRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {
    @Mock UserRepository users;
    @Mock WalletRepository wallets;
    @Mock TransactionRepository transactions;
    @Mock AuditService audit;
    WalletService service;

    @BeforeEach
    void setup() {
        service = new WalletService(users, wallets, transactions, audit);
    }

    private User recipient() {
        User user = new User("Recipient", "9000000002", "hash");
        ReflectionTestUtils.setField(user, "id", 2L);
        return user;
    }

    @Test
    void rejectsNegativeAmountsBeforeAccessingStorage() {
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        1L,
                                        new TransferRequest("9000000002", -1, ""),
                                        UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(users, wallets, transactions, audit);
    }

    @Test
    void rejectsSelfTransfer() {
        User user = recipient();
        when(users.findByPhone(anyString())).thenReturn(Optional.of(user));
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        2L,
                                        new TransferRequest("9000000002", 1, ""),
                                        UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(wallets, transactions);
    }

    @Test
    void resolvesIndianPrefixBeforeRejectingSelfTransfer() {
        when(users.findByPhone("9000000002")).thenReturn(Optional.empty());
        when(users.findByPhoneIn(any())).thenReturn(List.of(recipient()));
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        2L,
                                        new TransferRequest("9000000002", 69, ""),
                                        UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own wallet");
        verifyNoInteractions(wallets, transactions, audit);
    }

    @Test
    void missingRecipientHasActionableErrorAndDoesNotTouchBalances() {
        when(users.findByPhone(anyString())).thenReturn(Optional.empty());
        when(users.findByPhoneIn(any())).thenReturn(List.of());
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        1L,
                                        new TransferRequest("9000000002", 69, ""),
                                        UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo("RECIPIENT_NOT_FOUND"))
                .hasMessageContaining("register");
        verifyNoInteractions(wallets, transactions, audit);
    }

    @Test
    void ambiguousAliasesRequireExactRegisteredNumber() {
        when(users.findByPhone("9000000002")).thenReturn(Optional.empty());
        when(users.findByPhoneIn(any()))
                .thenReturn(List.of(recipient(), new User("Other", "+919000000002", "hash")));
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        1L,
                                        new TransferRequest("9000000002", 69, ""),
                                        UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("multiple WalletPay accounts");
        verifyNoInteractions(wallets, transactions, audit);
    }

    @Test
    void insufficientFundsDoesNotWriteTransaction() {
        when(users.findByPhone(anyString())).thenReturn(Optional.of(recipient()));
        when(wallets.lockById(1L)).thenReturn(Optional.of(new Wallet(1L)));
        when(wallets.lockById(2L)).thenReturn(Optional.of(new Wallet(2L)));
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        1L,
                                        new TransferRequest("9000000002", 1, ""),
                                        UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("insufficient");
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void receiverLimitDoesNotMutateSender() {
        Wallet sender = new Wallet(1L);
        sender.setBalanceMinor(100);
        Wallet receiver = new Wallet(2L);
        receiver.setBalanceMinor(Money.MAX_BALANCE);
        when(users.findByPhone(anyString())).thenReturn(Optional.of(recipient()));
        when(wallets.lockById(1L)).thenReturn(Optional.of(sender));
        when(wallets.lockById(2L)).thenReturn(Optional.of(receiver));
        assertThatThrownBy(
                        () ->
                                service.transfer(
                                        1L,
                                        new TransferRequest("9000000002", 1, ""),
                                        UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        assertThat(sender.getBalanceMinor()).isEqualTo(100);
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void completesAtomicTransferInOrderedLocks() {
        User recipient = recipient();
        Wallet sender = new Wallet(1L);
        sender.setBalanceMinor(100);
        Wallet receiver = new Wallet(2L);
        when(users.findByPhone(anyString())).thenReturn(Optional.of(recipient));
        when(users.findById(2L)).thenReturn(Optional.of(recipient));
        when(wallets.lockById(1L)).thenReturn(Optional.of(sender));
        when(wallets.lockById(2L)).thenReturn(Optional.of(receiver));
        when(transactions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result =
                service.transfer(
                        1L, new TransferRequest("9000000002", 40, "Lunch"), UUID.randomUUID());
        assertThat(sender.getBalanceMinor()).isEqualTo(60);
        assertThat(receiver.getBalanceMinor()).isEqualTo(40);
        assertThat(result.direction()).isEqualTo("DEBIT");
        var order = inOrder(wallets);
        order.verify(wallets).lockById(1L);
        order.verify(wallets).lockById(2L);
    }
}
