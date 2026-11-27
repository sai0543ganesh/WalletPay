package com.walletpay.repository;

import com.walletpay.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {}
