package com.walletpay.controller;

import com.walletpay.config.AppProperties;
import com.walletpay.dto.ApiModels.LoginRequest;
import com.walletpay.dto.ApiModels.RegisterRequest;
import com.walletpay.dto.ApiModels.UserResponse;
import com.walletpay.security.JwtAuthenticationFilter;
import com.walletpay.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final AppProperties properties;

    public AuthController(AuthService auth, AppProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = auth.register(request);
        setCookie(response, result);
        return result.user();
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = auth.login(request);
        setCookie(response, result);
        return result.user();
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return auth.profile((Long) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication, HttpServletResponse response) {
        auth.logout((Long) authentication.getPrincipal(), (UUID) authentication.getCredentials());
        clearCookie(response);
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private void setCookie(HttpServletResponse response, AuthService.LoginResult result) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie(result.token(), Duration.between(Instant.now(), result.expiresAt()))
                        .toString());
    }

    private ResponseCookie cookie(String value, Duration lifetime) {
        return ResponseCookie.from(JwtAuthenticationFilter.COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api")
                .maxAge(lifetime)
                .build();
    }
}
