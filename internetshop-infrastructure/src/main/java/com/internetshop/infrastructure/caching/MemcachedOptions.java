package com.internetshop.infrastructure.caching;

import java.time.Duration;
import java.util.Objects;

/**
 * ПР9: адреса сервера Memcached і таймаути (аналог секції "enyimMemcached" в appsettings.json).
 * Таймаути короткі навмисно: кеш має прискорювати відповідь, а не змушувати запит чекати недоступний сервер.
 */
public record MemcachedOptions(String host, int port, Duration timeout, Duration retryAfter) {

    public static final String DEFAULT_SERVER = "127.0.0.1:11211";

    public MemcachedOptions {
        Objects.requireNonNull(host);
        Objects.requireNonNull(timeout);
        Objects.requireNonNull(retryAfter);
        if (host.isBlank() || port < 1 || port > 65535) throw new IllegalArgumentException("Некоректна адреса Memcached.");
    }

    /** «host:port», наприклад 127.0.0.1:11211; таймаут 300 мс, повторна спроба після збою — через 5 с. */
    public static MemcachedOptions parse(String server) {
        String s = Objects.requireNonNull(server).trim();
        int colon = s.lastIndexOf(':');
        if (colon <= 0) throw new IllegalArgumentException("Очікується адреса Memcached у форматі host:port.");
        return new MemcachedOptions(s.substring(0, colon), Integer.parseInt(s.substring(colon + 1)),
                Duration.ofMillis(300), Duration.ofSeconds(5));
    }

    public String server() {
        return host + ":" + port;
    }
}
