package com.walletpay.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "balances")
public class Balance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long locked;

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User v) {
        user = v;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long v) {
        amount = v;
    }

    public long getLocked() {
        return locked;
    }

    public void setLocked(long v) {
        locked = v;
    }
}
