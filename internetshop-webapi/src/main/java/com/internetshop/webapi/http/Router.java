package com.internetshop.webapi.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Маршрутизація HTTP-запитів і єдина точка обробки помилок (аналог middleware у Program.cs з CourseDemo).
 * <p>
 * ПР3 (SOLID): Router лише знаходить маршрут і викликає обробник (SRP). Які винятки якому статусу
 * відповідають, вирішує ErrorMapper, переданий через конструктор (OCP + DIP), — тому Router не знає
 * класів Domain/Application. Запис відповідей і заголовків безпеки — у HttpResponses (SRP).
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
    private final ErrorMapper errors;

    public Router(ErrorMapper errors) {
        this.errors = Objects.requireNonNull(errors);
    }

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
                    HttpResponses.staticResource(exchange, staticFiles.get(path));
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
                    if (r.status() == 204) HttpResponses.empty(exchange, 204); // 204: відповідь без тіла
                    else HttpResponses.json(exchange, r.status(), r.body());
                    return;
                }
                if (pathMatched) throw new BadRequestException(405, "Метод не підтримується.");
                throw new BadRequestException(404, "Ресурс не знайдено.");
            } catch (BadRequestException e) {
                HttpResponses.error(exchange, e.status(), e.getMessage());
            } catch (RuntimeException e) {
                OptionalInt status = errors.statusFor(e);
                if (status.isPresent()) {
                    HttpResponses.error(exchange, status.getAsInt(), e.getMessage());
                } else {
                    // Подробиці помилки лише в лог сервера; клієнт не отримує стек та внутрішні дані.
                    LOG.log(Level.SEVERE, "Unhandled error for " + method + " " + path, e);
                    HttpResponses.error(exchange, 500, "Внутрішня помилка сервера.");
                }
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
}
