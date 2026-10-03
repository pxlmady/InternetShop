package com.internetshop.webapi.endpoints;

import com.internetshop.application.ordering.OrderDto;
import com.internetshop.application.ordering.OrderLineDto;
import com.internetshop.application.ordering.OrderService;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.http.Router.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP-маршрути контексту Ordering. Делегують сценарії OrderService. */
public final class OrderEndpoints {

    private OrderEndpoints() { }

    public static void register(Router router, OrderService orders) {
        router.route("GET", "/orders", req -> Response.ok(orders.list().stream().map(OrderEndpoints::json).toList()));
        router.route("GET", "/orders/{id}", req -> Response.ok(json(orders.get(req.pathId("id")))));
        router.route("POST", "/orders", req -> Response.created(json(orders.create(req.uuid("customerId")))));
        router.route("POST", "/orders/{id}/items", req -> Response.ok(json(
                orders.addItem(req.pathId("id"), req.uuid("productId"), req.integer("quantity")))));
        router.route("POST", "/orders/{id}/place", req -> Response.ok(json(orders.place(req.pathId("id")))));
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
