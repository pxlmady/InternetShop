package com.internetshop.webapi.endpoints;

import com.internetshop.application.catalog.CatalogUseCases;
import com.internetshop.application.catalog.ProductDto;
import com.internetshop.webapi.http.Request;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.http.Router.Response;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-маршрути контексту Catalog. Обробники лише читають запит, викликають сценарії Catalog
 * і перетворюють DTO на JSON — бізнес-правил тут немає.
 * <p>
 * ПР4 (DI): замість статичного методу з переданим сервісом — звичайний клас з ін'єкцією через конструктор
 * (аналог контролера ASP.NET Core). Залежить від абстракції CatalogUseCases; екземпляр на кожен запит
 * створює контейнер (Scoped).
 */
public final class ProductEndpoints {

    private final CatalogUseCases catalog;

    public ProductEndpoints(CatalogUseCases catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    /** Таблиця маршрутів: які методи класу обробляють які адреси. */
    public static void map(Router router) {
        router.route("GET", "/products", ProductEndpoints.class, ProductEndpoints::list);
        router.route("GET", "/products/{id}", ProductEndpoints.class, ProductEndpoints::get);
        router.route("POST", "/products", ProductEndpoints.class, ProductEndpoints::create);
        router.route("DELETE", "/products/{id}", ProductEndpoints.class, ProductEndpoints::delete);
    }

    Response list(Request req) {
        return Response.ok(catalog.list().stream().map(ProductEndpoints::json).toList());
    }

    Response get(Request req) {
        return Response.ok(json(catalog.get(req.pathId("id"))));
    }

    Response create(Request req) {
        return Response.created(json(catalog.create(req.string("name"), req.string("description"), req.decimal("price"))));
    }

    Response delete(Request req) {
        catalog.delete(req.pathId("id"));
        return Response.noContent();
    }

    static Map<String, Object> json(ProductDto p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("name", p.name());
        m.put("description", p.description());
        m.put("price", p.price());
        return m;
    }
}
