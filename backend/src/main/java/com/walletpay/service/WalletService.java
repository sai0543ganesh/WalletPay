package com.walletpay.service;

import com.walletpay.dto.ApiModels.BalanceResponse;
import com.walletpay.dto.ApiModels.DepositRequest;
import com.walletpay.dto.ApiModels.TransactionResponse;
import com.walletpay.dto.ApiModels.TransferRequest;
import com.walletpay.entity.User;
import com.walletpay.entity.Wallet;
import com.walletpay.entity.WalletTransaction;
import com.walletpay.exception.ApiException;
import com.walletpay.repository.TransactionRepository;
import com.walletpay.repository.UserRepository;
import com.walletpay.repository.WalletRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private final UserRepository users;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final AuditService audit;

    public WalletService(
            UserRepository users,
            WalletRepository wallets,
            TransactionRepository transactions,
            AuditService audit) {
        this.users = users;
        this.wallets = wallets;
        this.transactions = transactions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(Long userId) {
        Wallet wallet = wallets.findById(userId).orElseThrow(ApiException::notFound);
        return new BalanceResponse(wallet.getBalanceMinor(), "INR", wallet.getUpdatedAt());
    }

    @Transactional
    public TransactionResponse transfer(Long senderId, TransferRequest request, UUID key) {
        Money.validate(request.amountMinor());
        User recipient = resolveRecipient(request.recipientPhone());
        if (senderId.equals(recipient.getId()))
            throw ApiException.badRequest("You cannot transfer to your own wallet.");
        String note = request.note() == null ? "" : request.note().strip();
        String hash =
                Digests.sha256(
                        "TRANSFER\n"
                                + request.recipientPhone()
                                + "\n"
                                + request.amountMinor()
                                + "\n"
                                + note);
        // Lock in user-ID order, then check idempotency while concurrent requests are serialized.
        Wallet first =
                wallets.lockById(Math.min(senderId, recipient.getId()))
                        .orElseThrow(ApiException::notFound);
        Wallet second =
                wallets.lockById(Math.max(senderId, recipient.getId()))
                        .orElseThrow(ApiException::notFound);
        WalletTransaction existing =
                transactions.findBySenderIdAndIdempotencyKey(senderId, key).orElse(null);
        if (existing != null) return repeated(existing, hash, senderId);
        Wallet sender = first.getUserId().equals(senderId) ? first : second;
        Wallet receiver = first.getUserId().equals(senderId) ? second : first;
        long debited = Money.debit(sender.getBalanceMinor(), request.amountMinor());
        long credited = Money.credit(receiver.getBalanceMinor(), request.amountMinor());
        sender.setBalanceMinor(debited);
        receiver.setBalanceMinor(credited);
        WalletTransaction transaction =
                transactions.saveAndFlush(
                        new WalletTransaction(
                                "TRANSFER",
                                senderId,
                                recipient.getId(),
                                request.amountMinor(),
                                note,
                                key,
                                hash));
        audit.record(senderId, "TRANSFER_COMPLETED", transaction.getReference());
        return view(transaction, senderId);
    }

    @Transactional
    public TransactionResponse deposit(Long userId, DepositRequest request, UUID key) {
        Money.validate(request.amountMinor());
        if (!"SANDBOX".equals(request.provider()))
            throw ApiException.badRequest("Only the SANDBOX provider is supported.");
        String hash = Digests.sha256("DEPOSIT\n" + request.amountMinor() + "\nSANDBOX");
        wallets.lockById(userId).orElseThrow(ApiException::notFound);
        WalletTransaction existing =
                transactions.findBySenderIdAndIdempotencyKey(userId, key).orElse(null);
        if (existing != null) return repeated(existing, hash, userId);
        WalletTransaction transaction =
                transactions.saveAndFlush(
                        new WalletTransaction(
                                "DEPOSIT",
                                userId,
                                userId,
                                request.amountMinor(),
                                "Sandbox deposit",
                                key,
                                hash));
        audit.record(userId, "DEPOSIT_REQUESTED", transaction.getReference());
        return view(transaction, userId);
    }

    private TransactionResponse repeated(WalletTransaction transaction, String hash, Long userId) {
        if (!transaction.getRequestHash().equals(hash))
            throw ApiException.conflict(
                    "IDEMPOTENCY_CONFLICT",
                    "This idempotency key was already used for a different request.");
        return view(transaction, userId);
    }

    private User resolveRecipient(String phone) {
        var exact = users.findByPhone(phone);
        if (exact.isPresent()) return exact.get();
        // Existing account identities stay unchanged. Only unambiguous Indian mobile aliases
        // are accepted when the exact registered form is absent.
        Set<String> alternatives = Set.of();
        if (phone.matches("[6-9][0-9]{9}")) {
            alternatives = Set.of("+91" + phone, "91" + phone);
        } else if (phone.matches("\\+?91[6-9][0-9]{9}")) {
            String national = phone.substring(phone.length() - 10);
            alternatives =
                    phone.startsWith("+")
                            ? Set.of(national, "91" + national)
                            : Set.of(national, "+91" + national);
        }
        if (!alternatives.isEmpty()) {
            var matches = users.findByPhoneIn(alternatives);
            if (matches.size() == 1) return matches.getFirst();
            if (matches.size() > 1)
                throw ApiException.badRequest(
                        "This number matches multiple WalletPay accounts. Enter the exact registered number, including its country code.");
        }
        throw new ApiException(
                HttpStatus.NOT_FOUND,
                "RECIPIENT_NOT_FOUND",
                "No WalletPay account was found for this phone number. Ask the recipient to register, or check their registered number and country code.");
    }

    public TransactionResponse view(WalletTransaction transaction, Long userId) {
        boolean outgoing =
                transaction.getType().equals("TRANSFER")
                        && transaction.getSenderId().equals(userId);
        User other =
                transaction.getType().equals("TRANSFER")
                        ? users.findById(
                                        outgoing
                                                ? transaction.getRecipientId()
                                                : transaction.getSenderId())
                                .orElseThrow(ApiException::notFound)
                        : null;
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                transaction.getType(),
                outgoing ? "DEBIT" : "CREDIT",
                transaction.getStatus(),
                transaction.getAmountMinor(),
                transaction.getCurrency(),
                transaction.getNote(),
                other != null
                        ? other.getFullName()
                        : transaction.getType().equals("DEMO_CREDIT")
                                ? "WalletPay demo"
                                : "Sandbox provider",
                other == null ? null : other.getPhone(),
                transaction.getCreatedAt());
    }
}
