package com.internetshop.infrastructure.caching;

import com.internetshop.application.shared.caching.DistributedCache;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ПР9: розподілений кеш на Memcached — роль IMemcachedClient (Enyim.Caching) з методички.
 * Сторонніх бібліотек немає: клієнт сам говорить текстовим протоколом Memcached через TCP-сокет
 * (команди get / set / delete). Кеш живе в окремому процесі (сервері Memcached), тому його спільно бачать
 * усі екземпляри застосунку і він переживає перезапуск застосунку — на відміну від in-memory кешу.
 * <p>
 * Недоступність кешу не ламає застосунок: будь-яка мережева помилка = «промах» (get) або пропуск (set/remove).
 * Щоб кожен запит не чекав таймаут, після збою клієнт на {@code retryAfter} узагалі не звертається до сервера.
 * У журнал пишеться лише факт недоступності, без ключів і значень (у них дані замовлень).
 * Singleton: стану з'єднання немає (одне коротке з'єднання на операцію), спільний лише час наступної спроби.
 */
public final class MemcachedDistributedCache implements DistributedCache {

    private static final Logger LOG = Logger.getLogger(MemcachedDistributedCache.class.getName());
    /** Обмеження протоколу Memcached: ключ до 250 байт, без пробілів і керувальних символів. */
    private static final int MAX_KEY_BYTES = 250;
    /** TTL понад 30 днів Memcached трактує як абсолютний час Unix, тому більші значення не приймаємо. */
    private static final long MAX_TTL_SECONDS = 30L * 24 * 60 * 60;

    private final MemcachedOptions options;
    private volatile long unavailableUntil;

    public MemcachedDistributedCache(MemcachedOptions options) {
        this.options = Objects.requireNonNull(options);
        this.unavailableUntil = System.nanoTime();
    }

    @Override
    public Optional<String> get(String key) {
        byte[] k = checkKey(key);
        return execute(Optional.empty(), (in, out) -> {
            send(out, concat(bytes("get "), k, bytes("\r\n")));
            String header = readLine(in);
            if (header.equals("END")) return Optional.<String>empty();
            String[] parts = header.split(" ");
            if (parts.length < 4 || !parts[0].equals("VALUE")) throw new IOException("Unexpected reply");
            int length = Integer.parseInt(parts[3]);
            byte[] data = in.readNBytes(length);
            if (data.length != length) throw new IOException("Connection closed");
            readLine(in);                                             // \r\n після даних
            if (!readLine(in).equals("END")) throw new IOException("Unexpected reply");
            return Optional.of(new String(data, StandardCharsets.UTF_8));
        });
    }

    @Override
    public void set(String key, String value, Duration timeToLive) {
        byte[] k = checkKey(key);
        byte[] data = Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8);
        long ttl = (Objects.requireNonNull(timeToLive).toMillis() + 999) / 1000;  // округлення вгору до секунд
        if (ttl < 1 || ttl > MAX_TTL_SECONDS) throw new IllegalArgumentException("TTL має бути від 1 с до 30 днів.");
        execute(null, (in, out) -> {
            send(out, concat(bytes("set "), k, bytes(" 0 " + ttl + " " + data.length + "\r\n"), data, bytes("\r\n")));
            readLine(in);                                             // STORED або SERVER_ERROR — кеш необов'язковий
            return null;
        });
    }

    @Override
    public void remove(String key) {
        byte[] k = checkKey(key);
        execute(null, (in, out) -> {
            send(out, concat(bytes("delete "), k, bytes("\r\n")));
            readLine(in);                                             // DELETED або NOT_FOUND — обидва нормальні
            return null;
        });
    }

    // ---------------- з'єднання та протокол ----------------

    private interface Exchange<T> {
        T run(InputStream in, OutputStream out) throws IOException;
    }

    private <T> T execute(T fallback, Exchange<T> exchange) {
        if (System.nanoTime() - unavailableUntil < 0) return fallback;
        int timeout = (int) options.timeout().toMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(options.host(), options.port()), timeout);
            socket.setSoTimeout(timeout);
            return exchange.run(new BufferedInputStream(socket.getInputStream()), socket.getOutputStream());
        } catch (IOException | RuntimeException e) {
            unavailableUntil = System.nanoTime() + options.retryAfter().toNanos();
            LOG.log(Level.WARNING, "Memcached " + options.server() + " недоступний; працюємо без кешу ("
                    + e.getClass().getSimpleName() + ").");
            return fallback;
        }
    }

    private static byte[] checkKey(String key) {
        byte[] k = Objects.requireNonNull(key).getBytes(StandardCharsets.UTF_8);
        if (k.length == 0 || k.length > MAX_KEY_BYTES) throw new IllegalArgumentException("Некоректна довжина ключа кешу.");
        for (byte b : k) {
            if (b <= ' ' || b == 127) throw new IllegalArgumentException("Ключ кешу не може містити пробіли чи керувальні символи.");
        }
        return k;
    }

    private static void send(OutputStream out, byte[] command) throws IOException {
        out.write(command);
        out.flush();
    }

    /** Рядок відповіді до \r\n (без нього). */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int b = in.read(); ; b = in.read()) {
            if (b < 0) throw new IOException("Connection closed");
            if (b == '\n') break;
            if (line.size() > 1024) throw new IOException("Reply line too long");
            line.write(b);
        }
        String s = line.toString(StandardCharsets.US_ASCII);
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    private static byte[] bytes(String ascii) {
        return ascii.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] p : parts) all.writeBytes(p);
        return all.toByteArray();
    }
}
