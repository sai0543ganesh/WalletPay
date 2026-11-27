package com.walletpay.controller;

import com.walletpay.dto.ApiModels.AnalyticsResponse;
import com.walletpay.dto.ApiModels.PageResponse;
import com.walletpay.dto.ApiModels.TransactionResponse;
import com.walletpay.service.TransactionQueryService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TransactionController {
    private final TransactionQueryService queries;

    public TransactionController(TransactionQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionResponse> history(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return queries.history(
                (Long) auth.getPrincipal(),
                page,
                size,
                new TransactionQueryService.Filter(type, direction, status, search, from, to));
    }

    @GetMapping("/transactions/export")
    public ResponseEntity<byte[]> export(
            Authentication auth,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        String csv =
                queries.export(
                        (Long) auth.getPrincipal(),
                        new TransactionQueryService.Filter(
                                type, direction, status, search, from, to));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=walletpay-transactions.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/transactions/{id}/receipt")
    public TransactionResponse receipt(Authentication auth, @PathVariable UUID id) {
        return queries.receipt((Long) auth.getPrincipal(), id);
    }

    @GetMapping("/analytics")
    public AnalyticsResponse analytics(
            Authentication auth, @RequestParam(defaultValue = "30") int days) {
        return queries.analytics((Long) auth.getPrincipal(), days);
    }
}
