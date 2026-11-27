package com.walletpay.security;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Bounded, single-instance rate limiting. Distributed deployments require a shared limiter. */
@Component
public class WindowRateLimiter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long until, int count) {}

    public boolean allow(String key, int limit, Duration duration) {
        long now = System.currentTimeMillis();
        if (windows.size() >= 10_000) {
            windows.entrySet().removeIf(entry -> entry.getValue().until() <= now);
            if (windows.size() >= 10_000 && !windows.containsKey(key)) return false;
        }
        Window value =
                windows.compute(
                        key,
                        (ignored, old) ->
                                old == null || old.until() <= now
                                        ? new Window(now + duration.toMillis(), 1)
                                        : new Window(old.until(), old.count() + 1));
        return value.count() <= limit;
    }

    public void clear(String key) {
        windows.remove(key);
    }
}
