package com.walletpay.controller;

import com.walletpay.dto.ApiModels.BalanceResponse;
import com.walletpay.dto.ApiModels.DepositRequest;
import com.walletpay.dto.ApiModels.TransactionResponse;
import com.walletpay.dto.ApiModels.TransferRequest;
import com.walletpay.service.WalletService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WalletController {
    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @GetMapping("/wallet")
    public BalanceResponse balance(Authentication auth) {
        return wallets.balance((Long) auth.getPrincipal());
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(
            Authentication auth,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") UUID key) {
        return wallets.transfer((Long) auth.getPrincipal(), request, key);
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(
            Authentication auth,
            @Valid @RequestBody DepositRequest request,
            @RequestHeader("Idempotency-Key") UUID key) {
        return wallets.deposit((Long) auth.getPrincipal(), request, key);
    }
}
