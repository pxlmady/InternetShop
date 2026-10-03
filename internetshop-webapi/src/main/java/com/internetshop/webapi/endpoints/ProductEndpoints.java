package com.internetshop.webapi.endpoints;

import com.internetshop.application.catalog.CatalogService;
import com.internetshop.application.catalog.ProductDto;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.http.Router.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP-маршрути контексту Catalog. Обробники лише читають запит, викликають CatalogService
 * і перетворюють DTO на JSON — бізнес-правил тут немає.
 */
public final class ProductEndpoints {

    private ProductEndpoints() { }

    public static void register(Router router, CatalogService catalog) {
        router.route("GET", "/products", req -> Response.ok(catalog.list().stream().map(ProductEndpoints::json).toList()));
        router.route("GET", "/products/{id}", req -> Response.ok(json(catalog.get(req.pathId("id")))));
        router.route("POST", "/products", req -> Response.created(json(
                catalog.create(req.string("name"), req.string("description"), req.decimal("price")))));
        router.route("DELETE", "/products/{id}", req -> {
            catalog.delete(req.pathId("id"));
            return Response.noContent();
        });
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
