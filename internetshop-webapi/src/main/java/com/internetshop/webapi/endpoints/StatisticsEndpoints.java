package com.internetshop.webapi.endpoints;

import com.internetshop.application.analytics.SalesReport;
import com.internetshop.application.analytics.SalesStatistics;
import com.internetshop.webapi.http.Request;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.http.Router.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-маршрут для перегляду статистики, яку оновлюють обробники подій. Лише читання.
 * ПР4 (DI): залежить від абстракції SalesReport (а не від класу SalesStatistics), отриманої через конструктор.
 */
public final class StatisticsEndpoints {

    private final SalesReport report;

    public StatisticsEndpoints(SalesReport report) {
        this.report = Objects.requireNonNull(report);
    }

    public static void map(Router router) {
        router.route("GET", "/statistics", StatisticsEndpoints.class, StatisticsEndpoints::show);
    }

    Response show(Request req) {
        SalesStatistics.Snapshot s = report.snapshot();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ordersCreated", s.ordersCreated());
        m.put("ordersPlaced", s.ordersPlaced());
        m.put("itemsSold", s.itemsSold());
        m.put("revenue", s.revenue());
        return Response.ok(m);
    }
}
