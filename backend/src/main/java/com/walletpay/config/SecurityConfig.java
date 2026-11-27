package com.walletpay.config;

import com.walletpay.repository.SessionRepository;
import com.walletpay.security.ApiRateLimitFilter;
import com.walletpay.security.JwtAuthenticationFilter;
import com.walletpay.security.JwtService;
import com.walletpay.security.ProblemResponses;
import com.walletpay.security.WindowRateLimiter;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwt,
            SessionRepository sessions,
            WindowRateLimiter limiter,
            AppProperties properties)
            throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieCustomizer(
                cookie -> cookie.path("/").secure(properties.cookieSecure()).sameSite("Strict"));
        http.cors(Customizer.withDefaults())
                .csrf(
                        config ->
                                config.csrfTokenRepository(csrf)
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .ignoringRequestMatchers("/api/v1/webhooks/sandbox"))
                .sessionManagement(
                        config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .httpBasic(config -> config.disable())
                .formLogin(config -> config.disable())
                .logout(config -> config.disable())
                .authorizeHttpRequests(
                        config ->
                                config.requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/auth/csrf",
                                                "/actuator/health",
                                                "/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/auth/register",
                                                "/api/v1/auth/login",
                                                "/api/v1/webhooks/sandbox")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        config ->
                                config.authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        ProblemResponses.write(
                                                                request,
                                                                response,
                                                                401,
                                                                "Unauthorized",
                                                                "UNAUTHENTICATED",
                                                                "Please sign in to continue."))
                                        .accessDeniedHandler(
                                                (request, response, exception) ->
                                                        ProblemResponses.write(
                                                                request,
                                                                response,
                                                                403,
                                                                "Forbidden",
                                                                "ACCESS_DENIED",
                                                                "The request is not permitted or"
                                                                        + " the CSRF token is missing"
                                                                        + " or invalid.")))
                .headers(
                        config ->
                                config.contentSecurityPolicy(
                                        csp ->
                                                csp.policyDirectives(
                                                        "default-src 'none'; frame-ancestors"
                                                                + " 'none'")))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwt, sessions),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ApiRateLimitFilter(limiter), JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins =
                Arrays.stream(properties.allowedOrigins().split(","))
                        .map(String::strip)
                        .filter(s -> !s.isEmpty())
                        .toList();
        if (origins.stream().anyMatch(s -> s.contains("*")))
            throw new IllegalStateException("Wildcard CORS origins are not allowed.");
        config.setAllowedOrigins(origins);
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Request-ID", "Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
