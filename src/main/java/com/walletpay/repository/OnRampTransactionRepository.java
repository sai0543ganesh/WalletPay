package com.walletpay.repository;

import com.walletpay.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OnRampTransactionRepository extends JpaRepository<OnRampTransaction, Long> {
    Optional<OnRampTransaction> findByToken(String token);

    List<OnRampTransaction> findByUserIdOrderByStartTimeDesc(Long userId);
}
