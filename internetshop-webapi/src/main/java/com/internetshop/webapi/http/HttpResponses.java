package com.internetshop.webapi.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Запис HTTP-відповідей (ПР3, SRP): JSON, помилки, статичні файли і заголовки безпеки.
 * Раніше це теж робив Router; тепер він лише вибирає маршрут, а як формується відповідь — тут.
 */
final class HttpResponses {

    private HttpResponses() { }

    static void error(HttpExchange exchange, int status, String message) throws IOException {
        json(exchange, status, Map.of("error", message));
    }

    static void json(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        send(exchange, status, bytes);
    }

    /** Відповідь без тіла (204). */
    static void empty(HttpExchange exchange, int status) throws IOException {
        send(exchange, status, new byte[0]);
    }

    static void staticResource(HttpExchange exchange, String resourceName) throws IOException {
        byte[] bytes;
        try (InputStream in = HttpResponses.class.getResourceAsStream("/static/" + resourceName)) {
            if (in == null) throw new BadRequestException(404, "Ресурс не знайдено.");
            bytes = in.readAllBytes();
        }
        String type = resourceName.endsWith(".js") ? "text/javascript; charset=utf-8"
                : resourceName.endsWith(".css") ? "text/css; charset=utf-8"
                : "text/html; charset=utf-8";
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'");
        send(exchange, 200, bytes);
    }

    private static void send(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
