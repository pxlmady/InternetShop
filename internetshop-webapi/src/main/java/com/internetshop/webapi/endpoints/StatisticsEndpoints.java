package com.internetshop.webapi.endpoints;

import com.internetshop.application.analytics.SalesStatistics;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.http.Router.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP-маршрут для перегляду статистики, яку оновлюють обробники подій. Лише читання. */
public final class StatisticsEndpoints {

    private StatisticsEndpoints() { }

    public static void register(Router router, SalesStatistics statistics) {
        router.route("GET", "/statistics", req -> {
            SalesStatistics.Snapshot s = statistics.snapshot();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ordersCreated", s.ordersCreated());
            m.put("ordersPlaced", s.ordersPlaced());
            m.put("itemsSold", s.itemsSold());
            m.put("revenue", s.revenue());
            return Response.ok(m);
        });
    }
}
