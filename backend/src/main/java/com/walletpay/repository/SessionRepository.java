package com.walletpay.repository;

import com.walletpay.entity.AuthSession;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<AuthSession, UUID> {
    boolean existsByIdAndUserIdAndExpiresAtAfter(UUID id, Long userId, Instant time);

    void deleteByUserId(Long userId);

    void deleteByExpiresAtBefore(Instant time);
}
