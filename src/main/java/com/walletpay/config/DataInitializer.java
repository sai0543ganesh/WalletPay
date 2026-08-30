package com.walletpay.config;

import com.walletpay.entity.*;
import com.walletpay.repository.*;
import com.walletpay.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner seed(UserRepository users, BalanceRepository balances,
                           OnRampTransactionRepository onramps, PasswordEncoder encoder) {
        return args -> {
            seedUser(users, balances, onramps, encoder,
                    AuthService.DEMO_PHONE, "Demo User", 50000, "demo_initial_credit");
            // Additional real recipients make P2P testing possible.
            seedUser(users, balances, onramps, encoder,
                    "1111111111", "Alice", 20000, "alice_initial_credit");
            seedUser(users, balances, onramps, encoder,
                    "2222222222", "Bob", 2000, "bob_initial_credit");
        };
    }

    private void seedUser(UserRepository users, BalanceRepository balances,
                          OnRampTransactionRepository onramps, PasswordEncoder encoder,
                          String phone, String name, long amount, String token) {
        if (users.findByNumber(phone).isPresent()) return;

        User user = new User();
        user.setNumber(phone);
        user.setName(name);
        user.setPassword(encoder.encode("seed-password"));
        user = users.save(user);

        Balance balance = new Balance();
        balance.setUser(user);
        balance.setAmount(amount);
        balance.setLocked(0);
        balances.save(balance);

        OnRampTransaction tx = new OnRampTransaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setProvider("WalletPay Seed");
        tx.setToken(token);
        tx.setStatus(OnRampStatus.SUCCESS);
        tx.setStartTime(Instant.now());
        onramps.save(tx);
    }
}
