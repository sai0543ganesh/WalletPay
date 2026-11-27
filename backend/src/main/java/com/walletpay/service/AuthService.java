package com.walletpay.service;

import com.walletpay.dto.ApiModels.LoginRequest;
import com.walletpay.dto.ApiModels.PasswordRequest;
import com.walletpay.dto.ApiModels.ProfileRequest;
import com.walletpay.dto.ApiModels.RegisterRequest;
import com.walletpay.dto.ApiModels.UserResponse;
import com.walletpay.entity.AuthSession;
import com.walletpay.entity.User;
import com.walletpay.entity.Wallet;
import com.walletpay.exception.ApiException;
import com.walletpay.repository.SessionRepository;
import com.walletpay.repository.UserRepository;
import com.walletpay.repository.WalletRepository;
import com.walletpay.security.JwtService;
import com.walletpay.security.WindowRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public record LoginResult(UserResponse user, String token, Instant expiresAt) {}

    private final UserRepository users;
    private final WalletRepository wallets;
    private final SessionRepository sessions;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final WindowRateLimiter limiter;
    private final AuditService audit;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository users,
            WalletRepository wallets,
            SessionRepository sessions,
            PasswordEncoder encoder,
            JwtService jwt,
            WindowRateLimiter limiter,
            AuditService audit) {
        this.users = users;
        this.wallets = wallets;
        this.sessions = sessions;
        this.encoder = encoder;
        this.jwt = jwt;
        this.limiter = limiter;
        this.audit = audit;
        this.dummyPasswordHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult register(RegisterRequest request) {
        validatePassword(request.password());
        String name = request.fullName().strip();
        if (name.length() < 2)
            throw ApiException.badRequest("Full name must have at least two non-space characters.");
        if (users.findByPhone(request.phone()).isPresent())
            throw ApiException.conflict(
                    "ACCOUNT_EXISTS", "An account with this phone number already exists.");
        User user =
                users.saveAndFlush(
                        new User(name, request.phone(), encoder.encode(request.password())));
        wallets.saveAndFlush(new Wallet(user.getId()));
        audit.record(user.getId(), "ACCOUNT_REGISTERED", null);
        return createSession(user);
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String key = "login-phone:" + Digests.sha256(request.phone());
        if (!limiter.allow(key, 8, Duration.ofMinutes(15)))
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "LOGIN_RATE_LIMITED",
                    "Too many sign-in attempts. Try again in 15 minutes.");
        User user = users.lockByPhone(request.phone()).orElse(null);
        String candidate =
                request.password().getBytes(StandardCharsets.UTF_8).length <= 72
                        ? request.password()
                        : "invalid-length";
        boolean correct =
                encoder.matches(
                        candidate, user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !correct)
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Invalid phone number or password.");
        limiter.clear(key);
        audit.record(user.getId(), "SESSION_STARTED", null);
        return createSession(user);
    }

    private LoginResult createSession(User user) {
        UUID id = UUID.randomUUID();
        Instant expiry = jwt.expiresAt();
        sessions.deleteByExpiresAtBefore(Instant.now());
        sessions.saveAndFlush(new AuthSession(id, user.getId(), expiry));
        return new LoginResult(view(user), jwt.issue(user.getId(), id, expiry), expiry);
    }

    @Transactional(readOnly = true)
    public UserResponse profile(Long userId) {
        return view(users.findById(userId).orElseThrow(ApiException::notFound));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, ProfileRequest request) {
        User user = users.lockById(userId).orElseThrow(ApiException::notFound);
        String name = request.fullName().strip();
        if (name.length() < 2)
            throw ApiException.badRequest("Full name must have at least two non-space characters.");
        user.setFullName(name);
        audit.record(userId, "PROFILE_UPDATED", null);
        return view(user);
    }

    @Transactional
    public void changePassword(Long userId, PasswordRequest request) {
        validatePassword(request.newPassword());
        User user = users.lockById(userId).orElseThrow(ApiException::notFound);
        if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Current password is incorrect.");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        sessions.deleteByUserId(userId);
        audit.record(userId, "PASSWORD_CHANGED", null);
    }

    @Transactional
    public void logout(Long userId, UUID sessionId) {
        sessions.deleteById(sessionId);
        audit.record(userId, "SESSION_ENDED", null);
    }

    static void validatePassword(String password) {
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.badRequest(
                    "Password must have at least 12 characters and at most 72 UTF-8 bytes.");
    }

    private static UserResponse view(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getPhone());
    }
}
