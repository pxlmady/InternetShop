package com.internetshop.webapi.endpoints;

import com.internetshop.application.ordering.OrderDto;
import com.internetshop.application.ordering.OrderLineDto;
import com.internetshop.application.ordering.OrderUseCases;
import com.internetshop.webapi.http.Request;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.http.Router.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-маршрути контексту Ordering. Делегують сценарії замовлень.
 * ПР4 (DI): залежність від абстракції OrderUseCases через конструктор; екземпляр створює контейнер (Scoped).
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
        return Response.ok(orders.list().stream().map(OrderEndpoints::json).toList());
    }

    Response get(Request req) {
        return Response.ok(json(orders.get(req.pathId("id"))));
    }

    Response create(Request req) {
        return Response.created(json(orders.create(req.uuid("customerId"))));
    }

    Response addItem(Request req) {
        return Response.ok(json(orders.addItem(req.pathId("id"), req.uuid("productId"), req.integer("quantity"))));
    }

    Response place(Request req) {
        return Response.ok(json(orders.place(req.pathId("id"))));
    }

    static Map<String, Object> json(OrderDto o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.id());
        m.put("customerId", o.customerId());
        m.put("status", o.status());
        m.put("lines", o.lines().stream().map(OrderEndpoints::json).toList());
        m.put("total", o.total());
        return m;
    }

    private static Map<String, Object> json(OrderLineDto l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", l.productId());
        m.put("productName", l.productName());
        m.put("unitPrice", l.unitPrice());
        m.put("quantity", l.quantity());
        m.put("lineTotal", l.lineTotal());
        return m;
    }
}
