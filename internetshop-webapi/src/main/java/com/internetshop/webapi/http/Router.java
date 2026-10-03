package com.internetshop.webapi.http;

import com.internetshop.application.shared.ConcurrentChangeException;
import com.internetshop.application.shared.NotFoundException;
import com.internetshop.domain.shared.DomainRuleException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Маршрутизація HTTP-запитів та єдине місце перетворення помилок Domain/Application
 * на HTTP-відповіді (аналог middleware у Program.cs з CourseDemo).
 */
public final class Router implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(Router.class.getName());

    /** Результат обробника: HTTP-статус і об'єкт, який буде серіалізовано в JSON. */
    public record Response(int status, Object body) {
        public static Response ok(Object body) { return new Response(200, body); }
        public static Response created(Object body) { return new Response(201, body); }
        public static Response noContent() { return new Response(204, null); }
    }

    private record Route(String method, Pattern pattern, List<String> params, Function<Request, Response> handler) { }

    private final List<Route> routes = new ArrayList<>();
    private final Map<String, String> staticFiles = new HashMap<>();

    /** Шаблон на кшталт /orders/{id}/items; параметри — сегменти без «/». */
    public Router route(String method, String template, Function<Request, Response> handler) {
        List<String> params = new ArrayList<>();
        Matcher m = Pattern.compile("\\{(\\w+)}").matcher(template);
        StringBuilder regex = new StringBuilder();
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(template.substring(last, m.start()))).append("([^/]+)");
            params.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(template.substring(last)));
        routes.add(new Route(method, Pattern.compile(regex.toString()), params, handler));
        return this;
    }

    /** Статичний файл з classpath (папка resources/static). */
    public Router staticFile(String path, String resourceName) {
        staticFiles.put(path, resourceName);
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            try {
                if (staticFiles.containsKey(path)) {
                    if (!method.equals("GET")) throw new BadRequestException(405, "Метод не підтримується.");
                    sendStatic(exchange, staticFiles.get(path));
                    return;
                }
                boolean pathMatched = false;
                for (Route route : routes) {
                    Matcher m = route.pattern().matcher(path);
                    if (!m.matches()) continue;
                    pathMatched = true;
                    if (!route.method().equals(method)) continue;
                    Map<String, String> params = new HashMap<>();
                    for (int i = 0; i < route.params().size(); i++) params.put(route.params().get(i), m.group(i + 1));
                    if (method.equals("POST")) requireJson(exchange);
                    Response r = route.handler().apply(new Request(exchange, params));
                    if (r.status() == 204) send(exchange, 204, new byte[0]); // 204: відповідь без тіла
                    else sendJson(exchange, r.status(), r.body());
                    return;
                }
                if (pathMatched) throw new BadRequestException(405, "Метод не підтримується.");
                throw new BadRequestException(404, "Ресурс не знайдено.");
            } catch (DomainRuleException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (NotFoundException e) {
                sendError(exchange, 404, e.getMessage());
            } catch (ConcurrentChangeException e) {
                sendError(exchange, 409, e.getMessage());
            } catch (BadRequestException e) {
                sendError(exchange, e.status(), e.getMessage());
            } catch (RuntimeException e) {
                // Подробиці помилки лише в лог сервера; клієнт не отримує стек та внутрішні дані.
                LOG.log(Level.SEVERE, "Unhandled error for " + method + " " + path, e);
                sendError(exchange, 500, "Внутрішня помилка сервера.");
            }
        }
    }

    /**
     * POST приймається лише з Content-Type: application/json. Звичайна HTML-форма зі стороннього сайту
     * не може надіслати такий запит без CORS-перевірки, тому це базовий захист від CSRF.
     */
    private static void requireJson(HttpExchange exchange) {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/json"))
            throw new BadRequestException(415, "Потрібен Content-Type: application/json.");
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message));
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        send(exchange, status, bytes);
    }

    private static void sendStatic(HttpExchange exchange, String resourceName) throws IOException {
        byte[] bytes;
        try (InputStream in = Router.class.getResourceAsStream("/static/" + resourceName)) {
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
