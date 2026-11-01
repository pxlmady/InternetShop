package com.internetshop.checks;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ПР9: мінімальний сервер з текстовим протоколом Memcached (get / set / delete) для перевірок.
 * Перевірки не залежать від того, чи запущено справжній Memcached, і бачать кожну команду клієнта
 * (наприклад, з яким TTL записано значення). Слухає лише loopback.
 */
final class FakeMemcachedServer implements AutoCloseable {

    private record Entry(String value, long expiresAtNanos) { }

    private final ServerSocket server;
    private final Map<String, Entry> data = new ConcurrentHashMap<>();
    /** Журнал команд: «get order:…», «set order:… 60», «delete order:…». */
    final List<String> commands = new CopyOnWriteArrayList<>();

    FakeMemcachedServer() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread.ofVirtual().start(this::acceptLoop);
    }

    String address() {
        return "127.0.0.1:" + server.getLocalPort();
    }

    Optional<String> peek(String key) {
        Entry e = data.get(key);
        return e == null || System.nanoTime() - e.expiresAtNanos() >= 0 ? Optional.empty() : Optional.of(e.value());
    }

    void put(String key, String value) {
        data.put(key, new Entry(value, System.nanoTime() + 60_000_000_000L));
    }

    long count(String prefix) {
        return commands.stream().filter(c -> c.startsWith(prefix)).count();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                Thread.ofVirtual().start(() -> serve(s));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            for (String line = readLine(in); line != null; line = readLine(in)) {
                String[] p = line.split(" ");
                switch (p[0]) {
                    case "get" -> {
                        commands.add("get " + p[1]);
                        Optional<String> v = peek(p[1]);
                        if (v.isPresent()) {
                            byte[] bytes = v.get().getBytes(StandardCharsets.UTF_8);
                            out.write(("VALUE " + p[1] + " 0 " + bytes.length + "\r\n").getBytes(StandardCharsets.US_ASCII));
                            out.write(bytes);
                            out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                        }
                        out.write("END\r\n".getBytes(StandardCharsets.US_ASCII));
                    }
                    case "set" -> {
                        long ttl = Long.parseLong(p[3]);
                        byte[] bytes = in.readNBytes(Integer.parseInt(p[4]));
                        readLine(in);
                        commands.add("set " + p[1] + " " + ttl);
                        data.put(p[1], new Entry(new String(bytes, StandardCharsets.UTF_8), System.nanoTime() + ttl * 1_000_000_000L));
                        out.write("STORED\r\n".getBytes(StandardCharsets.US_ASCII));
                    }
                    case "delete" -> {
                        commands.add("delete " + p[1]);
                        out.write((data.remove(p[1]) != null ? "DELETED\r\n" : "NOT_FOUND\r\n").getBytes(StandardCharsets.US_ASCII));
                    }
                    default -> out.write("ERROR\r\n".getBytes(StandardCharsets.US_ASCII));
                }
                out.flush();
            }
        } catch (IOException | RuntimeException ignored) {
            // з'єднання закрито клієнтом
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int b = in.read(); ; b = in.read()) {
            if (b < 0) return line.size() == 0 ? null : line.toString(StandardCharsets.UTF_8);
            if (b == '\n') break;
            if (b != '\r') line.write(b);
        }
        return line.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
