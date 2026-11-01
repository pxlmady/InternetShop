package com.internetshop.webapi.endpoints;

import com.internetshop.application.ordering.OrderUseCases;
import com.internetshop.application.ordering.dto.AddOrderItemRequest;
import com.internetshop.application.ordering.dto.CreateOrderRequest;
import com.internetshop.webapi.http.Request;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.http.Router.Response;

import java.util.Objects;

/**
 * HTTP-маршрути контексту Ordering. Делегують сценарії замовлень.
 * ПР4 (DI): залежність від абстракції OrderUseCases через конструктор; екземпляр створює контейнер (Scoped).
 * <p>
 * ПР6 (DTO): ендпоінт працює виключно з DTO (аналог OrdersController з методички):
 * тіло HTTP-запиту → DTO запиту (CreateOrderRequest, AddOrderItemRequest) → сценарій → DTO результату,
 * який повертається як є і серіалізується в JSON (як return Ok(result) в ASP.NET Core).
 * Доменних класів ендпоінт не імпортує і не бачить.
 * <p>
 * ПР8 (валідація): перевірок у ендпоінті немає. Він лише описує, як прочитати DTO з JSON (bindCreate, bindAddItem);
 * CreateOrderRequestValidator і AddOrderItemRequestValidator запускаються автоматично до виклику create/addItem.
 */
public final class OrderEndpoints {

    private final OrderUseCases orders;

    public OrderEndpoints(OrderUseCases orders) {
        this.orders = Objects.requireNonNull(orders);
    }

    public static void map(Router router) {
        router.route("GET", "/orders", OrderEndpoints.class, OrderEndpoints::list);
        router.route("GET", "/orders/{id}", OrderEndpoints.class, OrderEndpoints::get);
        // ПР8: маршрути з тілом — спершу binder (JSON → DTO), потім автоматична валідація DTO, потім ендпоінт.
        router.route("POST", "/orders", OrderEndpoints.class, OrderEndpoints::bindCreate, OrderEndpoints::create);
        router.route("POST", "/orders/{id}/items", OrderEndpoints.class, OrderEndpoints::bindAddItem, OrderEndpoints::addItem);
        router.route("POST", "/orders/{id}/place", OrderEndpoints.class, OrderEndpoints::place);
    }

    // ---- model binding: лише формат JSON, без правил ----

    static CreateOrderRequest bindCreate(Request req) {
        return new CreateOrderRequest(req.uuid("customerId"));
    }

    static AddOrderItemRequest bindAddItem(Request req) {
        return new AddOrderItemRequest(req.uuid("productId"), req.integer("quantity"));
    }

    // ---- дії: модель уже перевірена валідатором, ендпоінт лише викликає use-case ----

    Response list(Request req) {
        return Response.ok(orders.list());
    }

    Response get(Request req) {
        return Response.ok(orders.get(req.pathId("id")));
    }

    Response create(Request req, CreateOrderRequest body) {
        return Response.created(orders.create(body));
    }

    Response addItem(Request req, AddOrderItemRequest body) {
        return Response.ok(orders.addItem(req.pathId("id"), body));
    }

    Response place(Request req) {
        return Response.ok(orders.place(req.pathId("id")));
    }
}
