package com.internetshop.webapi.endpoints;

import com.internetshop.application.catalog.CatalogUseCases;
import com.internetshop.application.catalog.dto.CreateProductRequest;
import com.internetshop.webapi.http.Request;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.http.Router.Response;

import java.util.Objects;

/**
 * HTTP-маршрути контексту Catalog. Обробники лише читають запит, викликають сценарії Catalog
 * і повертають DTO — бізнес-правил тут немає.
 * <p>
 * ПР4 (DI): замість статичного методу з переданим сервісом — звичайний клас з ін'єкцією через конструктор
 * (аналог контролера ASP.NET Core). Залежить від абстракції CatalogUseCases; екземпляр на кожен запит
 * створює контейнер (Scoped).
 * <p>
 * ПР6 (DTO): тіло запиту перетворюється на CreateProductRequest, а відповіддю є DTO (ProductDto,
 * CreatedProductDto), які серіалізуються в JSON без ручного перекладання полів.
 * <p>
 * ПР8 (валідація): ендпоінт не перевіряє поля сам — CreateProductRequestValidator запускається конвеєром
 * Router до виклику create, тож сюди потрапляє вже коректна модель.
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
        router.route("POST", "/products", ProductEndpoints.class, ProductEndpoints::bindCreate, ProductEndpoints::create);
        router.route("DELETE", "/products/{id}", ProductEndpoints.class, ProductEndpoints::delete);
    }

    /** ПР8: model binding — JSON → DTO; правила перевіряє CreateProductRequestValidator автоматично. */
    static CreateProductRequest bindCreate(Request req) {
        return new CreateProductRequest(req.string("name"), req.string("description"), req.decimal("price"));
    }

    Response list(Request req) {
        return Response.ok(catalog.list());
    }

    Response get(Request req) {
        return Response.ok(catalog.get(req.pathId("id")));
    }

    Response create(Request req, CreateProductRequest body) {
        return Response.created(catalog.create(body));
    }

    Response delete(Request req) {
        catalog.delete(req.pathId("id"));
        return Response.noContent();
    }
}
