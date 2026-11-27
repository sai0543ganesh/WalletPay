package com.walletpay.service;

import com.walletpay.exception.ApiException;

public final class Money {
    public static final long MAX_TRANSACTION = 100_000_000L;
    public static final long MAX_BALANCE = 10_000_000_000L;

    private Money() {}

    public static void validate(long amount) {
        if (amount < 1 || amount > MAX_TRANSACTION)
            throw ApiException.badRequest("Amount must be between 1 and 100000000 paise.");
    }

    public static long credit(long balance, long amount) {
        validate(amount);
        long result;
        try {
            result = Math.addExact(balance, amount);
        } catch (ArithmeticException ex) {
            throw ApiException.conflict(
                    "BALANCE_LIMIT", "The wallet balance limit would be exceeded.");
        }
        if (balance < 0 || result > MAX_BALANCE)
            throw ApiException.conflict(
                    "BALANCE_LIMIT", "The wallet balance limit would be exceeded.");
        return result;
    }

    public static long debit(long balance, long amount) {
        validate(amount);
        if (balance < amount)
            throw ApiException.conflict(
                    "INSUFFICIENT_BALANCE", "Your wallet has insufficient funds.");
        return Math.subtractExact(balance, amount);
    }
}
