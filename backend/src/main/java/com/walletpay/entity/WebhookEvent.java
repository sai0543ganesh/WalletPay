package com.walletpay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "deposit_id", nullable = false)
    private UUID depositId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WebhookEvent() {}

    public WebhookEvent(UUID eventId, UUID depositId, String payloadHash) {
        this.eventId = eventId;
        this.depositId = depositId;
        this.payloadHash = payloadHash;
        this.receivedAt = Instant.now();
    }

    public String getPayloadHash() {
        return payloadHash;
    }
}
