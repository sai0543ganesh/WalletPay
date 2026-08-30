package com.walletpay.service;

import com.walletpay.dto.AuthRequest;
import com.walletpay.dto.AuthResponse;
import com.walletpay.entity.Balance;
import com.walletpay.entity.User;
import com.walletpay.repository.BalanceRepository;
import com.walletpay.repository.UserRepository;
import com.walletpay.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public static final String DEMO_PHONE = "0987654321";

    private final UserRepository users;
    private final BalanceRepository balances;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, BalanceRepository balances, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.balances = balances;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse login(AuthRequest req) {
        User user = users.findByNumber(req.phone())
                .orElseThrow(() -> new IllegalArgumentException("Invalid phone number or password"));

        // Reserved demo account: any non-empty password is accepted.
        if (!DEMO_PHONE.equals(req.phone()) && !encoder.matches(req.password(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid phone number or password");
        }

        return response(user);
    }

    @Transactional
    public AuthResponse register(AuthRequest req) {
        if (DEMO_PHONE.equals(req.phone())) {
            throw new IllegalArgumentException("This phone number is reserved for the demo account");
        }
        if (users.findByNumber(req.phone()).isPresent()) {
            throw new IllegalArgumentException("An account with this phone number already exists. Please sign in.");
        }

        User user = new User();
        user.setNumber(req.phone());
        user.setName("Wallet User");
        user.setPassword(encoder.encode(req.password()));
        user = users.save(user);

        Balance balance = new Balance();
        balance.setUser(user);
        balance.setAmount(0);
        balance.setLocked(0);
        balances.save(balance);

        return response(user);
    }

    private AuthResponse response(User user) {
        return new AuthResponse(user.getId(), user.getName(), user.getNumber(), jwt.generate(user.getId()));
    }
}
