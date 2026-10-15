package com.internetshop.webapi;

import com.internetshop.application.analytics.OrderCreatedStatisticsHandler;
import com.internetshop.application.analytics.OrderPlacedStatisticsHandler;
import com.internetshop.application.analytics.SalesStatistics;
import com.internetshop.application.catalog.CatalogService;
import com.internetshop.application.catalog.ProductStore;
import com.internetshop.application.notifications.NotificationOutbox;
import com.internetshop.application.notifications.OrderPlacedNotificationHandler;
import com.internetshop.application.ordering.OrderService;
import com.internetshop.application.ordering.OrderStore;
import com.internetshop.application.ordering.ProductCatalog;
import com.internetshop.application.ordering.events.OrderCreated;
import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.infrastructure.events.InProcessEventBus;
import com.internetshop.infrastructure.integration.CatalogProductAdapter;
import com.internetshop.infrastructure.notifications.InMemoryNotificationOutbox;
import com.internetshop.infrastructure.persistence.FileOrderStore;
import com.internetshop.infrastructure.persistence.FileProductStore;
import com.internetshop.webapi.endpoints.NotificationEndpoints;
import com.internetshop.webapi.endpoints.OrderEndpoints;
import com.internetshop.webapi.endpoints.ProductEndpoints;
import com.internetshop.webapi.endpoints.StatisticsEndpoints;
import com.internetshop.webapi.http.Router;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Точка складання (Composition Root): лише тут вибираються конкретні реалізації контрактів
 * і з'єднуються шари. Внутрішні шари про цей вибір не знають.
 */
public final class InternetShopApp {

    private InternetShopApp() { }

    public static HttpServer start(int port, Path dataDirectory) throws IOException {
        // Infrastructure: реалізації контрактів Application.
        ProductStore productStore = new FileProductStore(dataDirectory);
        OrderStore orderStore = new FileOrderStore(dataDirectory);
        InProcessEventBus eventBus = new InProcessEventBus();
        NotificationOutbox notifications = new InMemoryNotificationOutbox();

        // Application: сценарії отримують лише інтерфейси.
        CatalogService catalog = new CatalogService(productStore);
        ProductCatalog productCatalog = new CatalogProductAdapter(catalog); // Ordering → Catalog
        OrderService orders = new OrderService(orderStore, productCatalog, eventBus); // producer подій
        SalesStatistics statistics = new SalesStatistics();

        // Event-Driven: хто на яку подію реагує, вирішується лише тут. OrderService про це не знає;
        // нова реакція = новий обробник + один рядок підписки, без змін у сценаріях замовлень.
        eventBus.subscribe(OrderCreated.class, new OrderCreatedStatisticsHandler(statistics));
        eventBus.subscribe(OrderPlaced.class, new OrderPlacedNotificationHandler(notifications));
        eventBus.subscribe(OrderPlaced.class, new OrderPlacedStatisticsHandler(statistics));

        seedCatalog(catalog);

        Router router = new Router()
                .staticFile("/", "index.html")
                .staticFile("/app.js", "app.js")
                .staticFile("/styles.css", "styles.css");
        ProductEndpoints.register(router, catalog);
        OrderEndpoints.register(router, orders);
        NotificationEndpoints.register(router, notifications);
        StatisticsEndpoints.register(router, statistics);

        // Слухаємо лише loopback (localhost): застосунок не доступний з інших комп'ютерів мережі.
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    /** Початкові дані для демонстрації — створюються через звичайний сценарій, тож правила діють. */
    private static void seedCatalog(CatalogService catalog) {
        if (!catalog.list().isEmpty()) return;
        catalog.create("Ноутбук 14\"", "Легкий ноутбук для навчання", new BigDecimal("32999.00"));
        catalog.create("Бездротова миша", "2.4 ГГц, тихі кнопки", new BigDecimal("649.50"));
        catalog.create("USB-C кабель", "1 м, 60 Вт", new BigDecimal("199"));
    }
}
