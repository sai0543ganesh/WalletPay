package com.walletpay.controller;

import com.walletpay.dto.ApiModels.DemoFundsResponse;
import com.walletpay.dto.ApiModels.TransactionResponse;
import com.walletpay.exception.ApiException;
import com.walletpay.service.DemoFundsService;
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
@RequestMapping("/api/v1/demo-funds")
public class DemoFundsController {
    private final DemoFundsService funds;

    public DemoFundsController(DemoFundsService funds) {
        this.funds = funds;
    }

    @GetMapping
    public DemoFundsResponse status(Authentication auth) {
        return funds.status((Long) auth.getPrincipal());
    }

    @PostMapping("/welcome")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse welcome(
            Authentication auth,
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody(required = false) byte[] body) {
        // The grant amount is fixed by the server; a request cannot supply any amount.
        if (body != null && body.length > 0) {
            throw ApiException.badRequest("The welcome-credit request must have an empty body.");
        }
        return funds.claimWelcome((Long) auth.getPrincipal(), key);
    }
}
