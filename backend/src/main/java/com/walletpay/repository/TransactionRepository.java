package com.walletpay.repository;

import com.walletpay.entity.WalletTransaction;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
        extends JpaRepository<WalletTransaction, UUID>,
                JpaSpecificationExecutor<WalletTransaction> {
    Optional<WalletTransaction> findBySenderIdAndIdempotencyKey(Long senderId, UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WalletTransaction t where t.id = :id")
    Optional<WalletTransaction> lockById(@Param("id") UUID id);
}
