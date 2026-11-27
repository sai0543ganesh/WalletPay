package com.walletpay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {
    @Id private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 160)
    private String note;

    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected WalletTransaction() {}

    public WalletTransaction(
            String type,
            Long senderId,
            Long recipientId,
            long amountMinor,
            String note,
            UUID idempotencyKey,
            String requestHash) {
        this.id = UUID.randomUUID();
        this.reference = "WP-" + id.toString().replace("-", "").toUpperCase();
        this.type = type;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.amountMinor = amountMinor;
        this.currency = "INR";
        this.note = note;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = Instant.now();
        boolean completed = type.equals("TRANSFER") || type.equals("DEMO_CREDIT");
        this.status = completed ? "COMPLETED" : "PENDING";
        this.completedAt = completed ? createdAt : null;
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public Long getSenderId() {
        return senderId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getNote() {
        return note;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void complete() {
        status = "COMPLETED";
        completedAt = Instant.now();
    }
}
