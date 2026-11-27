package com.walletpay.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "onramp_transactions")
public class OnRampTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OnRampStatus status;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private Instant startTime;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    public Long getId() {
        return id;
    }

    public OnRampStatus getStatus() {
        return status;
    }

    public void setStatus(OnRampStatus v) {
        status = v;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String v) {
        token = v;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String v) {
        provider = v;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long v) {
        amount = v;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant v) {
        startTime = v;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User v) {
        user = v;
    }
}
