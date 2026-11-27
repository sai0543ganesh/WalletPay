package com.walletpay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "wallets")
public class Wallet {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "demo_welcome_claimed", nullable = false)
    private boolean demoWelcomeClaimed;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Wallet() {}

    public Wallet(Long userId) {
        this.userId = userId;
        this.updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public boolean isDemoWelcomeClaimed() {
        return demoWelcomeClaimed;
    }

    public void markDemoWelcomeClaimed() {
        demoWelcomeClaimed = true;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setBalanceMinor(long balanceMinor) {
        this.balanceMinor = balanceMinor;
        this.updatedAt = Instant.now();
    }
}
