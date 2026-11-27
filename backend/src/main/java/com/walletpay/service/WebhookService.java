package com.walletpay.service;

import com.walletpay.config.AppProperties;
import com.walletpay.dto.ApiModels.WebhookRequest;
import com.walletpay.entity.Wallet;
import com.walletpay.entity.WalletTransaction;
import com.walletpay.entity.WebhookEvent;
import com.walletpay.exception.ApiException;
import com.walletpay.repository.TransactionRepository;
import com.walletpay.repository.WalletRepository;
import com.walletpay.repository.WebhookEventRepository;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebhookService {
    private final AppProperties properties;
    private final TransactionRepository transactions;
    private final WalletRepository wallets;
    private final WebhookEventRepository events;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final Validator validator;

    public WebhookService(
            AppProperties properties,
            TransactionRepository transactions,
            WalletRepository wallets,
            WebhookEventRepository events,
            AuditService audit,
            ObjectMapper mapper,
            Validator validator) {
        this.properties = properties;
        this.transactions = transactions;
        this.wallets = wallets;
        this.events = events;
        this.audit = audit;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional
    public void receive(String timestamp, String signature, String body) {
        if (!properties.sandboxWebhookEnabled()) throw ApiException.notFound();
        authenticate(timestamp, signature, body, properties.webhookSecret(), Instant.now());
        WebhookRequest request;
        try {
            request = mapper.readValue(body, WebhookRequest.class);
        } catch (tools.jackson.core.JacksonException ex) {
            throw ApiException.badRequest("Webhook JSON is invalid.");
        }
        if (request == null || !validator.validate(request).isEmpty())
            throw ApiException.badRequest("Webhook fields are invalid.");
        WalletTransaction transaction =
                transactions.lockById(request.depositId()).orElseThrow(ApiException::notFound);
        if (!transaction.getType().equals("DEPOSIT")
                || transaction.getAmountMinor() != request.amountMinor()
                || !transaction.getCurrency().equals(request.currency())) {
            throw ApiException.conflict(
                    "WEBHOOK_MISMATCH", "Webhook does not match the deposit intent.");
        }
        String hash = Digests.sha256(body);
        WebhookEvent previous = events.findById(request.eventId()).orElse(null);
        if (previous != null) {
            if (!previous.getPayloadHash().equals(hash))
                throw ApiException.conflict(
                        "WEBHOOK_REPLAY_CONFLICT",
                        "The event ID was already used for different data.");
            return;
        }
        Wallet wallet =
                wallets.lockById(transaction.getRecipientId()).orElseThrow(ApiException::notFound);
        if (transaction.getStatus().equals("PENDING")) {
            wallet.setBalanceMinor(Money.credit(wallet.getBalanceMinor(), request.amountMinor()));
            transaction.complete();
            audit.record(
                    transaction.getRecipientId(),
                    "SANDBOX_DEPOSIT_COMPLETED",
                    transaction.getReference());
        }
        events.saveAndFlush(new WebhookEvent(request.eventId(), transaction.getId(), hash));
    }

    public static void authenticate(
            String timestamp, String signature, String body, String secret, Instant now) {
        try {
            long seconds = Long.parseLong(timestamp);
            if (seconds < now.getEpochSecond() - 300 || seconds > now.getEpochSecond() + 300)
                throw new IllegalArgumentException();
            byte[] received = HexFormat.of().parseHex(signature);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected =
                    mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, received)) throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_WEBHOOK_SIGNATURE",
                    "Webhook signature or timestamp is invalid.");
        }
    }
}
