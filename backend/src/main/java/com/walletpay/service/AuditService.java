package com.walletpay.service;

import com.walletpay.entity.AuditEvent;
import com.walletpay.repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public void record(Long actor, String action, String reference) {
        repository.save(new AuditEvent(actor, action, reference));
        log.info("Security audit action={} actor={} reference={}", action, actor, reference);
    }
}
