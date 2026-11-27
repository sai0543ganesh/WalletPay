package com.walletpay.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> domain(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status())
                .body(problem(ex.status(), ex.code(), ex.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail p =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_FAILED",
                        "Please check the submitted fields.",
                        request);
        Map<String, String> fields = new TreeMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        p.setProperty("fieldErrors", fields);
        return ResponseEntity.badRequest().body(p);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingRequestHeaderException.class,
        ConstraintViolationException.class,
        IllegalArgumentException.class
    })
    ResponseEntity<ProblemDetail> malformed(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        problem(
                                HttpStatus.BAD_REQUEST,
                                "INVALID_REQUEST",
                                "The request format or parameters are invalid.",
                                request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        problem(
                                HttpStatus.CONFLICT,
                                "CONFLICT",
                                "The request conflicts with existing data.",
                                request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> absent(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        problem(
                                HttpStatus.NOT_FOUND,
                                "NOT_FOUND",
                                "The requested resource was not found.",
                                request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(
                        problem(
                                HttpStatus.METHOD_NOT_ALLOWED,
                                "METHOD_NOT_ALLOWED",
                                "This HTTP method is not supported for this resource.",
                                request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {
        log.error(
                "Request {} failed: {}",
                request.getAttribute("requestId"),
                ex.getClass().getSimpleName());
        return ResponseEntity.internalServerError()
                .body(
                        problem(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "INTERNAL_ERROR",
                                "The request could not be completed. Please try again.",
                                request));
    }

    public static ProblemDetail problem(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase());
        p.setType(
                URI.create(
                        "urn:walletpay:error:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        p.setInstance(URI.create(request.getRequestURI()));
        p.setProperty("code", code);
        p.setProperty("requestId", String.valueOf(request.getAttribute("requestId")));
        return p;
    }
}
