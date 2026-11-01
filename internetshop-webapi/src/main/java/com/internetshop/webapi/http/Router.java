package com.internetshop.webapi.http;

import com.internetshop.webapi.di.ServiceScope;
import com.internetshop.webapi.di.ServiceScopeFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiFunction;
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
 * <p>
 * ПР4 (DI): для кожного HTTP-запиту Router створює окрему область контейнера (scope) через абстракцію
 * ServiceScopeFactory і отримує з неї клас-ендпоінт разом з усіма його залежностями (аналог активації
 * контролерів в ASP.NET Core). Тому Scoped-залежності живуть рівно один запит, а ендпоінти й сервіси
 * не знають про контейнер — вони лише отримують абстракції через конструктор.
 * <p>
 * ПР8 (валідація): для маршрутів з тілом запиту конвеєр має три кроки, як в ASP.NET Core з FluentValidation:
 * 1) model binding — JSON перетворюється на DTO (тут лише перевірка формату: тип поля, коректний UUID);
 * 2) автоматична валідація DTO (ModelValidator, у точці складання — AutoValidation) ще до виклику ендпоінта;
 * 3) ендпоінт викликає use-case вже з перевіреною моделлю.
 * Router про конкретні валідатори не знає — лише про абстракцію ModelValidator.
 */
public final class Router implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(Router.class.getName());

    /** Результат обробника: HTTP-статус і об'єкт, який буде серіалізовано в JSON. */
    public record Response(int status, Object body) {
        public static Response ok(Object body) { return new Response(200, body); }
        public static Response created(Object body) { return new Response(201, body); }
        public static Response noContent() { return new Response(204, null); }
    }

    /** Обробник маршруту отримує область поточного запиту, щоб з неї створити клас-ендпоінт. */
    private interface Handler {
        Response handle(ServiceScope scope, Request request);
    }

    /** ПР8: ендпоінт з уже прочитаною і перевіреною моделлю тіла запиту (аналог дії контролера з [FromBody]). */
    @FunctionalInterface
    public interface BodyAction<E, B> {
        Response handle(E endpoint, Request request, B body);
    }

    /** ПР8: автоматична перевірка вхідної моделі перед виконанням ендпоінта (реалізація — у точці складання). */
    @FunctionalInterface
    public interface ModelValidator {
        /** Кидає виняток, якщо модель некоректна; валідатори беруться з області поточного запиту. */
        void validate(ServiceScope scope, Object model);
    }

    private record Route(String method, Pattern pattern, List<String> params, Handler handler) { }

    private final List<Route> routes = new ArrayList<>();
    private final Map<String, String> staticFiles = new HashMap<>();
    private final ErrorMapper errors;
    private final ServiceScopeFactory scopes;
    private ModelValidator modelValidator = (scope, model) -> { };

    public Router(ErrorMapper errors, ServiceScopeFactory scopes) {
        this.errors = Objects.requireNonNull(errors);
        this.scopes = Objects.requireNonNull(scopes);
    }

    /**
     * Маршрут, який обробляє клас-ендпоінт: на кожен запит його екземпляр отримується з області запиту,
     * тобто контейнер сам створює ендпоінт і передає в конструктор усі залежності.
     */
    public <E> Router route(String method, String template, Class<E> endpoint, BiFunction<E, Request, Response> action) {
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(action);
        return add(method, template, (scope, request) -> action.apply(scope.get(endpoint), request));
    }

    /**
     * ПР8: маршрут з тілом запиту. binder читає JSON у DTO (model binding), потім модель автоматично
     * перевіряється ModelValidator-ом і лише після цього створюється ендпоінт і виконується use-case.
     */
    public <E, B> Router route(String method, String template, Class<E> endpoint,
                               Function<Request, B> binder, BodyAction<E, B> action) {
        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(binder);
        Objects.requireNonNull(action);
        return add(method, template, (scope, request) -> {
            B body = binder.apply(request);       // 1. JSON → DTO (помилки формату → 400)
            modelValidator.validate(scope, body); // 2. автоматична валідація до use-case
            return action.handle(scope.get(endpoint), request, body); // 3. ендпоінт → use-case
        });
    }

    /** ПР8: підключає автоматичну валідацію моделей (аналог AddFluentValidationAutoValidation). */
    public Router useModelValidation(ModelValidator validator) {
        this.modelValidator = Objects.requireNonNull(validator);
        return this;
    }

    /** Простий маршрут без залежностей. */
    public Router route(String method, String template, Function<Request, Response> handler) {
        Objects.requireNonNull(handler);
        return add(method, template, (scope, request) -> handler.apply(request));
    }

    /** Шаблон на кшталт /orders/{id}/items; параметри — сегменти без «/». */
    private Router add(String method, String template, Handler handler) {
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
                    Response r;
                    // Одна область (scope) на один HTTP-запит; після відповіді вона закривається.
                    try (ServiceScope scope = scopes.createScope()) {
                        r = route.handler().handle(scope, new Request(exchange, params));
                    }
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
                    // Тіло відповіді теж задає правило ErrorMapper: за замовчуванням {"error": "..."},
                    // для помилок валідації (ПР8) — структурована відповідь з помилками за полями.
                    HttpResponses.json(exchange, status.getAsInt(), errors.bodyFor(e));
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
