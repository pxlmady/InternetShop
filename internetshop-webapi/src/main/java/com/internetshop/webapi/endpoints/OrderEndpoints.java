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
 */
public final class OrderEndpoints {

    private final OrderUseCases orders;

    public OrderEndpoints(OrderUseCases orders) {
        this.orders = Objects.requireNonNull(orders);
    }

    public static void map(Router router) {
        router.route("GET", "/orders", OrderEndpoints.class, OrderEndpoints::list);
        router.route("GET", "/orders/{id}", OrderEndpoints.class, OrderEndpoints::get);
        router.route("POST", "/orders", OrderEndpoints.class, OrderEndpoints::create);
        router.route("POST", "/orders/{id}/items", OrderEndpoints.class, OrderEndpoints::addItem);
        router.route("POST", "/orders/{id}/place", OrderEndpoints.class, OrderEndpoints::place);
    }

    Response list(Request req) {
        return Response.ok(orders.list());
    }

    Response get(Request req) {
        return Response.ok(orders.get(req.pathId("id")));
    }

    Response create(Request req) {
        CreateOrderRequest body = new CreateOrderRequest(req.uuid("customerId"));
        return Response.created(orders.create(body));
    }

    Response addItem(Request req) {
        AddOrderItemRequest body = new AddOrderItemRequest(req.uuid("productId"), req.integer("quantity"));
        return Response.ok(orders.addItem(req.pathId("id"), body));
    }

    Response place(Request req) {
        return Response.ok(orders.place(req.pathId("id")));
    }
}
