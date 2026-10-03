package com.internetshop.webapi.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP-запит з уже розібраними параметрами шляху та зручним читанням полів JSON.
 * Тут лише перевірка формату; бізнес-правила перевіряє Domain.
 */
public final class Request {

    static final int MAX_BODY_BYTES = 16 * 1024;

    private final HttpExchange exchange;
    private final Map<String, String> pathParams;
    private Map<String, Object> body;

    Request(HttpExchange exchange, Map<String, String> pathParams) {
        this.exchange = exchange;
        this.pathParams = pathParams;
    }

    public UUID pathId(String name) {
        return toUuid(pathParams.get(name), "Некоректний ідентифікатор у адресі.");
    }

    public String string(String field) {
        Object v = body().get(field);
        if (v == null) return null;
        if (v instanceof String s) return s;
        throw new BadRequestException("Поле '" + field + "' має бути рядком.");
    }

    public BigDecimal decimal(String field) {
        Object v = body().get(field);
        if (v == null) return null;
        if (v instanceof BigDecimal d) return d;
        throw new BadRequestException("Поле '" + field + "' має бути числом.");
    }

    /** Відсутнє ціле поле повертається як 0 — Domain відхилить його своїм правилом. */
    public int integer(String field) {
        Object v = body().get(field);
        if (v == null) return 0;
        if (v instanceof BigDecimal d) {
            try {
                return d.intValueExact();
            } catch (ArithmeticException e) {
                throw new BadRequestException("Поле '" + field + "' має бути цілим числом.");
            }
        }
        throw new BadRequestException("Поле '" + field + "' має бути цілим числом.");
    }

    public UUID uuid(String field) {
        Object v = body().get(field);
        if (v == null) return null;
        if (v instanceof String s) return toUuid(s, "Поле '" + field + "' має бути ідентифікатором (UUID).");
        throw new BadRequestException("Поле '" + field + "' має бути ідентифікатором (UUID).");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body() {
        if (body != null) return body;
        String text = readBody();
        Object parsed;
        try {
            parsed = Json.parse(text.isBlank() ? "{}" : text);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Некоректний JSON у тілі запиту.");
        }
        if (!(parsed instanceof Map)) throw new BadRequestException("Тіло запиту має бути JSON-об'єктом.");
        body = (Map<String, Object>) parsed;
        return body;
    }

    private String readBody() {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                if (out.size() > MAX_BODY_BYTES) throw new BadRequestException(413, "Тіло запиту завелике.");
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BadRequestException("Не вдалося прочитати тіло запиту.");
        }
    }

    private static UUID toUuid(String text, String error) {
        try {
            UUID id = UUID.fromString(text);
            // UUID.fromString приймає й скорочені форми; вимагаємо канонічний запис.
            if (!id.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException(error);
        }
    }
}
