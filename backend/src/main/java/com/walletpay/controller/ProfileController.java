package com.walletpay.controller;

import com.walletpay.dto.ApiModels.PasswordRequest;
import com.walletpay.dto.ApiModels.ProfileRequest;
import com.walletpay.dto.ApiModels.UserResponse;
import com.walletpay.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final AuthService auth;
    private final AuthController sessions;

    public ProfileController(AuthService auth, AuthController sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    @GetMapping
    public UserResponse profile(Authentication authentication) {
        return auth.profile((Long) authentication.getPrincipal());
    }

    @PutMapping
    public UserResponse update(
            Authentication authentication, @Valid @RequestBody ProfileRequest request) {
        return auth.updateProfile((Long) authentication.getPrincipal(), request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(
            Authentication authentication,
            @Valid @RequestBody PasswordRequest request,
            HttpServletResponse response) {
        auth.changePassword((Long) authentication.getPrincipal(), request);
        sessions.clearCookie(response);
    }
}
