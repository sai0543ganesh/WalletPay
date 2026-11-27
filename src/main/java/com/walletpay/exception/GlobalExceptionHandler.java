package com.walletpay.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, NoSuchElementException.class})
    public ResponseEntity<Map<String, String>> handle(RuntimeException e) {
        HttpStatus s =
                e instanceof NoSuchElementException ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(s).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(
            org.springframework.web.bind.MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", e.getBindingResult().getFieldError().getDefaultMessage()));
    }
}
