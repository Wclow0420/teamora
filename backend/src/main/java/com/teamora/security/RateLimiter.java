package com.teamora.security;

import com.teamora.common.exception.TooManyRequestsException;
import com.teamora.config.TeamoraProperties;
import com.teamora.config.TeamoraProperties.Limit;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory token-bucket rate limiter for the public auth endpoints (login,
 * register, forgot/reset password). Each key — {@code "login:email:…"},
 * {@code "login:ip:…"} — gets a bucket of {@code max} tokens that refills
 * continuously over {@code window}. Single-instance only (no Redis): with more
 * than one API instance each enforces its own limits, which still caps abuse.
 * Over the limit → {@link TooManyRequestsException} (429).
 */
@Component
public class RateLimiter {

    /** Above this many tracked keys, idle (full) buckets are swept. */
    private static final int SWEEP_THRESHOLD = 20_000;
    private static final long SWEEP_EVERY_NANOS = 60_000_000_000L;

    private final TeamoraProperties.RateLimit limits;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong(System.nanoTime());

    public RateLimiter(TeamoraProperties props) {
        this.limits = props.rateLimitOrDefault();
    }

    public void checkLogin(String email, String ip) {
        check("login:ip:" + ip, limits.loginPerIp());
        if (email != null && !email.isBlank()) {
            check("login:email:" + email.trim().toLowerCase(Locale.ROOT), limits.loginPerEmail());
        }
    }

    public void checkRegister(String ip) {
        check("register:ip:" + ip, limits.registerPerIp());
    }

    public void checkForgotPassword(String ip) {
        check("forgot:ip:" + ip, limits.forgotPasswordPerIp());
    }

    public void checkResetPassword(String ip) {
        check("reset:ip:" + ip, limits.resetPasswordPerIp());
    }

    /** Take one token for {@code key}, or throw 429 with the seconds until one is free. */
    void check(String key, Limit limit) {
        if (!limits.isEnabled()) {
            return;
        }
        long now = System.nanoTime();
        sweepIfNeeded(now);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(limit, now));
        long waitNanos = bucket.tryTake(now);
        if (waitNanos > 0) {
            throw new TooManyRequestsException((waitNanos + 999_999_999L) / 1_000_000_000L);
        }
    }

    private void sweepIfNeeded(long now) {
        long last = lastSweep.get();
        if (buckets.size() < SWEEP_THRESHOLD || now - last < SWEEP_EVERY_NANOS || !lastSweep.compareAndSet(last, now)) {
            return;
        }
        buckets.entrySet().removeIf(e -> e.getValue().isFull(now));
    }

    private static final class Bucket {
        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long updatedAt;

        Bucket(Limit limit, long now) {
            this.capacity = limit.max();
            this.refillPerNano = limit.max() / (double) limit.window().toNanos();
            this.tokens = capacity;
            this.updatedAt = now;
        }

        /** 0 when a token was taken, else nanos until one will be available. */
        synchronized long tryTake(long now) {
            refill(now);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return 0;
            }
            return (long) Math.ceil((1.0 - tokens) / refillPerNano);
        }

        synchronized boolean isFull(long now) {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(long now) {
            long elapsed = now - updatedAt;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
                updatedAt = now;
            }
        }
    }
}
