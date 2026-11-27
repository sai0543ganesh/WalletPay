package com.walletpay.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Fixed security messages avoid leaking exception internals before MVC advice is available. */
public final class ProblemResponses {
    private ProblemResponses() {}

    public static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String title,
            String code,
            String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter()
                .write(
                        "{\"status\":%d,\"title\":\"%s\",\"code\":\"%s\",\"detail\":\"%s\",\"requestId\":\"%s\"}"
                                .formatted(
                                        status,
                                        title,
                                        code,
                                        detail,
                                        request.getAttribute("requestId")));
    }
}
