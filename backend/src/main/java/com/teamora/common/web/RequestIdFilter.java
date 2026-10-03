package com.teamora.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Gives every request an id: the caller's {@code X-Request-Id} when it's a sane
 * token, else a fresh one. It is put in the logging MDC ({@code requestId}, shown
 * on every log line) and echoed back in the {@code X-Request-Id} response header,
 * so a user-reported error can be matched to the server logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && SAFE.matcher(incoming).matches() ? incoming : newId();
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The current request's id (or null outside a request). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    private static String newId() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
