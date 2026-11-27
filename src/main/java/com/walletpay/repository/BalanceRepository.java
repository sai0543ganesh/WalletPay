package com.walletpay.repository;

import com.walletpay.entity.Balance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface BalanceRepository extends JpaRepository<Balance, Long> {
    Optional<Balance> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Balance b where b.user.id=:userId")
    Optional<Balance> findByUserIdForUpdate(@Param("userId") Long userId);
}
