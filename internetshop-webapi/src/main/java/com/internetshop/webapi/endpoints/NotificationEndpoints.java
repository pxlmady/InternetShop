package com.internetshop.webapi.endpoints;

import com.internetshop.application.notifications.Notification;
import com.internetshop.application.notifications.NotificationFeed;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.http.Router.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP-маршрут для перегляду сповіщень, створених обробником події OrderPlaced. Лише читання:
 * маршрут отримує вузький контракт NotificationFeed, тож створити сповіщення через нього неможливо (ПР3, ISP).
 */
public final class NotificationEndpoints {

    private NotificationEndpoints() { }

    public static void register(Router router, NotificationFeed feed) {
        router.route("GET", "/notifications", req -> Response.ok(feed.findRecent().stream()
                .map(NotificationEndpoints::json).toList()));
    }

    private static Map<String, Object> json(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerId", n.customerId());
        m.put("orderId", n.orderId());
        m.put("message", n.message());
        m.put("createdAt", n.createdAt().toString());
        return m;
    }
}
