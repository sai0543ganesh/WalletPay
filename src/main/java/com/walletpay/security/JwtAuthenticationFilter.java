package com.walletpay.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends org.springframework.web.filter.OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthenticationFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain
    ) throws ServletException, IOException {

        String h = req.getHeader("Authorization");

        if (h != null && h.startsWith("Bearer ")) {
            try {
                Long id = jwt.extractUserId(h.substring(7));

                var auth = new UsernamePasswordAuthenticationToken(
                        id,
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

                auth.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(req)
                );

                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(req, res);
    }
}