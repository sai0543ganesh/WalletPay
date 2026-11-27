package com.walletpay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.walletpay.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {
    @Test
    void preservesExactMinorUnits() {
        assertThat(Money.credit(120, 5)).isEqualTo(125);
        assertThat(Money.debit(125, 5)).isEqualTo(120);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE, 100_000_001L, Long.MAX_VALUE})
    void rejectsInvalidAmounts(long amount) {
        assertThatThrownBy(() -> Money.validate(amount)).isInstanceOf(ApiException.class);
    }

    @Test
    void preventsNegativeBalance() {
        assertThatThrownBy(() -> Money.debit(99, 100))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("insufficient");
    }

    @Test
    void preventsBalanceOverflow() {
        assertThatThrownBy(() -> Money.credit(Long.MAX_VALUE, 1)).isInstanceOf(ApiException.class);
    }

    @Test
    void enforcesConfiguredBalanceCap() {
        assertThatThrownBy(() -> Money.credit(Money.MAX_BALANCE, 1))
                .isInstanceOf(ApiException.class);
    }
}
