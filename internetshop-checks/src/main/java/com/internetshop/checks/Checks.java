package com.internetshop.checks;

import com.internetshop.application.analytics.OrderCreatedStatisticsHandler;
import com.internetshop.application.analytics.OrderPlacedStatisticsHandler;
import com.internetshop.application.analytics.SalesStatistics;
import com.internetshop.application.catalog.CatalogService;
import com.internetshop.application.catalog.ProductNotFoundException;
import com.internetshop.application.notifications.Notification;
import com.internetshop.application.notifications.OrderPlacedNotificationHandler;
import com.internetshop.application.ordering.OrderDto;
import com.internetshop.application.ordering.OrderNotFoundException;
import com.internetshop.application.ordering.OrderService;
import com.internetshop.application.ordering.ProductNotInCatalogException;
import com.internetshop.application.ordering.events.OrderCreated;
import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.application.shared.events.Event;
import com.internetshop.application.shared.ConcurrentChangeException;
import com.internetshop.domain.catalog.Product;
import com.internetshop.domain.catalog.ProductName;
import com.internetshop.domain.ordering.CustomerId;
import com.internetshop.domain.ordering.Order;
import com.internetshop.domain.ordering.OrderStatus;
import com.internetshop.domain.ordering.Quantity;
import com.internetshop.domain.shared.DomainRuleException;
import com.internetshop.domain.shared.Money;
import com.internetshop.infrastructure.events.InProcessEventBus;
import com.internetshop.infrastructure.integration.CatalogProductAdapter;
import com.internetshop.infrastructure.notifications.InMemoryNotificationOutbox;
import com.internetshop.infrastructure.persistence.FileOrderStore;
import com.internetshop.infrastructure.persistence.FileProductStore;
import com.internetshop.webapi.InternetShopApp;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Консольні перевірки (аналог CourseDemo.Checks): Domain -> Application + Infrastructure ->
 * події та обробники (ПР2) -> межі шарів і контекстів -> HTTP. Використовують окрему тимчасову папку даних.
 * Запуск: mvn verify (або з кореня проєкту); при першій невдалій перевірці — виняток і помилка збірки.
 */
public final class Checks {

    private static int passed;

    public static void main(String[] args) throws Exception {
        domainCatalog();
        domainOrdering();
        Path tmp = Files.createTempDirectory("internetshop-checks-");
        try {
            persistenceAndContexts(tmp.resolve("store"));
            events(tmp.resolve("events"));
            architecture(Path.of(args.length > 0 ? args[0] : "."));
            http(tmp.resolve("http"));
        } finally {
            deleteRecursively(tmp);
        }
        System.out.println("All " + passed + " checks passed.");
    }

    // ---------------- Domain: Catalog ----------------

    private static void domainCatalog() {
        reject(() -> new ProductName("   "), "Blank product name rejected");
        check(new ProductName("  Миша ").equals(new ProductName("Миша")), "Value object equality and normalization");
        reject(() -> new Product(new ProductName("Миша"), "", Money.ZERO), "Zero price rejected");
        check(new Money(new BigDecimal("10")).equals(Money.of("10.00")), "Money equality ignores scale (10 == 10.00)");
        reject(() -> Money.of("1.999"), "Money with more than 2 decimals rejected");
        reject(() -> Money.of("1e999999999"), "Huge amount rejected without overflow");
    }

    // ---------------- Domain: Ordering ----------------

    private static void domainOrdering() {
        reject(() -> new CustomerId(new UUID(0, 0)), "Empty customer id rejected");
        reject(() -> new Quantity(0), "Quantity 0 rejected");
        reject(() -> new Quantity(100), "Quantity 100 rejected");

        Order order = new Order(new CustomerId(UUID.randomUUID()));
        UUID mouse = UUID.randomUUID();
        reject(order::place, "Empty order cannot be placed");
        order.addItem(mouse, "Миша", Money.of("100.50"), new Quantity(2));
        check(order.getLines().size() == 1 && order.total().equals(Money.of("201")), "Item added and total calculated");
        order.addItem(mouse, "Миша", Money.of("100.50"), new Quantity(3));
        check(order.getLines().size() == 1 && order.getLines().get(0).getQuantity().value() == 5,
                "Same product merges quantity instead of a new line");
        reject(() -> order.addItem(mouse, "Миша", Money.of("100.50"), new Quantity(95)), "Merged quantity above 99 rejected");
        check(order.getLines().get(0).getQuantity().value() == 5, "Rejected operation does not alter aggregate");
        reject(() -> order.addItem(UUID.randomUUID(), "Дорогий", Money.of("1000000000"), new Quantity(99)),
                "Order total above limit rejected");
        try {
            order.getLines().clear();
            throw new AssertionError("FAIL: lines collection is modifiable");
        } catch (UnsupportedOperationException e) {
            check(true, "Lines can be changed only through the aggregate root");
        }
        order.place();
        check(order.getStatus() == OrderStatus.PLACED, "Order placed");
        reject(() -> order.addItem(UUID.randomUUID(), "Кабель", Money.of("10"), new Quantity(1)), "Placed order is immutable");
        reject(order::place, "Order cannot be placed twice");
    }

    // ---------------- Application + Infrastructure + взаємодія контекстів ----------------

    private static void persistenceAndContexts(Path dir) {
        CatalogService catalog = new CatalogService(new FileProductStore(dir));
        OrderService orders = new OrderService(new FileOrderStore(dir), new CatalogProductAdapter(catalog), new InProcessEventBus());
        UUID productId = catalog.create("Ноутбук", "14 дюймів", new BigDecimal("30000")).id();
        UUID orderId = orders.create(UUID.randomUUID()).id();
        orders.addItem(orderId, productId, 1);
        orders.place(orderId);

        // Нові екземпляри сховищ = «перезапуск застосунку»: дані читаються з файлів.
        CatalogService catalog2 = new CatalogService(new FileProductStore(dir));
        FileOrderStore orderStore2 = new FileOrderStore(dir);
        OrderService orders2 = new OrderService(orderStore2, new CatalogProductAdapter(catalog2), new InProcessEventBus());
        OrderDto saved = orders2.get(orderId);
        check(catalog2.get(productId).name().equals("Ноутбук")
                && saved.status().equals("PLACED") && saved.lines().size() == 1
                && saved.lines().get(0).productName().equals("Ноутбук")
                && saved.total().compareTo(new BigDecimal("30000")) == 0, "Data survives restart (new store instances)");
        expect(DomainRuleException.class, () -> orders2.addItem(orderId, productId, 1), "Domain rules hold after rehydration");
        expect(OrderNotFoundException.class, () -> orders2.get(UUID.randomUUID()), "Missing order reported");

        UUID draftId = orders2.create(UUID.randomUUID()).id();
        expect(ProductNotInCatalogException.class, () -> orders2.addItem(draftId, UUID.randomUUID(), 1),
                "Ordering asks Catalog through ProductCatalog contract: unknown product rejected");
        expect(DomainRuleException.class, () -> orders2.create(null), "Missing customer id rejected by domain");

        // Імітуємо два запити, які одночасно прочитали ту саму чернетку.
        Order first = orderStore2.findById(draftId).orElseThrow();
        Order second = orderStore2.findById(draftId).orElseThrow();
        first.addItem(productId, "Ноутбук", Money.of("30000"), new Quantity(1));
        second.addItem(productId, "Ноутбук", Money.of("30000"), new Quantity(2));
        orderStore2.update(first);
        expect(ConcurrentChangeException.class, () -> orderStore2.update(second), "Concurrent update rejected (optimistic version)");
        check(orders2.get(draftId).lines().get(0).quantity() == 1, "Failed concurrent update leaves first result intact");

        // Видалення товару з каталогу (Catalog) не змінює вже сформовані замовлення (Ordering).
        catalog2.delete(productId);
        expect(ProductNotFoundException.class, () -> catalog2.get(productId), "Deleted product is no longer in catalog");
        check(new CatalogService(new FileProductStore(dir)).list().stream().noneMatch(p -> p.id().equals(productId)),
                "Product deletion survives restart");
        check(orders2.get(orderId).lines().get(0).productName().equals("Ноутбук"),
                "Existing order keeps its snapshot after product deletion");
        UUID anotherDraft = orders2.create(UUID.randomUUID()).id();
        expect(ProductNotInCatalogException.class, () -> orders2.addItem(anotherDraft, productId, 1),
                "Deleted product cannot be added to a new order");
        expect(ProductNotFoundException.class, () -> catalog2.delete(productId), "Deleting a missing product reported");
    }

    // ---------------- ПР2: події та обробники (Event-Driven) ----------------

    private static void events(Path dir) {
        CatalogService catalog = new CatalogService(new FileProductStore(dir));
        InProcessEventBus bus = new InProcessEventBus();
        List<Event> published = new ArrayList<>();
        bus.subscribe(Event.class, published::add); // «спостерігач» за всіма подіями — лише для перевірок
        OrderService orders = new OrderService(new FileOrderStore(dir), new CatalogProductAdapter(catalog), bus);

        UUID productId = catalog.create("Миша", "", new BigDecimal("100.50")).id();
        UUID customerId = UUID.randomUUID();
        UUID orderId = orders.create(customerId).id();
        check(published.size() == 1 && published.get(0) instanceof OrderCreated c && c.orderId().equals(orderId),
                "Creating an order publishes OrderCreated");

        orders.addItem(orderId, productId, 3);
        check(published.size() == 1, "Adding an item publishes no event");

        UUID emptyId = orders.create(customerId).id();
        published.clear();
        expect(DomainRuleException.class, () -> orders.place(emptyId), "Empty order still rejected by domain");
        check(published.isEmpty(), "Rejected operation publishes no event");

        orders.place(orderId);
        check(published.size() == 1 && published.get(0) instanceof OrderPlaced p && p.orderId().equals(orderId)
                        && p.customerId().equals(customerId) && p.itemCount() == 3
                        && p.total().compareTo(new BigDecimal("301.50")) == 0,
                "Placing an order publishes OrderPlaced with customer, item count and total");

        // Обробники окремо: кожен відповідає за свою реакцію.
        InMemoryNotificationOutbox outbox = new InMemoryNotificationOutbox();
        OrderPlaced event = (OrderPlaced) published.get(0);
        new OrderPlacedNotificationHandler(outbox).handle(event);
        Notification n = outbox.findRecent().get(0);
        check(n.customerId().equals(customerId) && n.orderId().equals(orderId) && n.message().contains("301.50"),
                "Notification handler creates a notification for the customer");
        SalesStatistics stats = new SalesStatistics();
        new OrderCreatedStatisticsHandler(stats).handle(new OrderCreated(orderId, event.occurredAt()));
        new OrderPlacedStatisticsHandler(stats).handle(event);
        SalesStatistics.Snapshot s = stats.snapshot();
        check(s.ordersCreated() == 1 && s.ordersPlaced() == 1 && s.itemsSold() == 3
                && s.revenue().compareTo(new BigDecimal("301.50")) == 0, "Statistics handlers update statistics");

        // Шина: один producer, кілька незалежних consumers; збій одного не зачіпає інших і саму операцію.
        InProcessEventBus bus2 = new InProcessEventBus();
        InMemoryNotificationOutbox outbox2 = new InMemoryNotificationOutbox();
        SalesStatistics stats2 = new SalesStatistics();
        bus2.subscribe(OrderPlaced.class, e -> { throw new IllegalStateException("handler failure"); });
        bus2.subscribe(OrderPlaced.class, new OrderPlacedNotificationHandler(outbox2));
        bus2.subscribe(OrderPlaced.class, new OrderPlacedStatisticsHandler(stats2));
        OrderService orders2 = new OrderService(new FileOrderStore(dir), new CatalogProductAdapter(catalog), bus2);
        UUID order2 = orders2.create(customerId).id();
        orders2.addItem(order2, productId, 1);
        Logger busLog = Logger.getLogger(InProcessEventBus.class.getName());
        Level previous = busLog.getLevel();
        busLog.setLevel(Level.OFF); // очікуваний збій обробника не засмічує вивід перевірок
        OrderDto placed;
        try {
            placed = orders2.place(order2);
        } finally {
            busLog.setLevel(previous);
        }
        check(placed.status().equals("PLACED") && orders2.get(order2).status().equals("PLACED"),
                "Failing handler does not undo the saved operation");
        check(outbox2.findRecent().size() == 1 && stats2.snapshot().ordersPlaced() == 1,
                "Other handlers still receive the event after one fails");
        check(stats2.snapshot().ordersCreated() == 0, "Event reaches only handlers subscribed to its type");

        for (int i = 0; i < 60; i++) outbox2.add(n);
        check(outbox2.findRecent().size() == 50, "Notification outbox keeps a bounded number of messages");
    }

    // ---------------- Межі шарів і bounded contexts ----------------

    private static void architecture(Path root) throws IOException {
        Path domain = root.resolve("internetshop-domain/src/main/java");
        Path application = root.resolve("internetshop-application/src/main/java");
        if (!Files.isDirectory(domain) || !Files.isDirectory(application))
            throw new AssertionError("FAIL: run checks from the project root (or pass it as the first argument)");

        check(importsOf(domain).stream().allMatch(i -> i.startsWith("java.") || i.startsWith("com.internetshop.domain.")),
                "Domain depends only on the JDK and itself");
        check(importsOf(application).stream().noneMatch(i -> i.startsWith("com.internetshop.infrastructure")
                        || i.startsWith("com.internetshop.webapi") || i.startsWith("com.sun.net") || i.startsWith("java.nio.file")),
                "Application does not depend on Infrastructure, WebApi, HTTP or file system");
        List<String> ordering = new ArrayList<>(importsOf(domain.resolve("com/internetshop/domain/ordering")));
        ordering.addAll(importsOf(application.resolve("com/internetshop/application/ordering")));
        check(ordering.stream().noneMatch(i -> i.contains(".catalog.")), "Ordering context does not use Catalog classes");
        List<String> catalog = new ArrayList<>(importsOf(domain.resolve("com/internetshop/domain/catalog")));
        catalog.addAll(importsOf(application.resolve("com/internetshop/application/catalog")));
        check(catalog.stream().noneMatch(i -> i.contains(".ordering.")), "Catalog context does not use Ordering classes");

        // ПР2: ініціатор подій не залежить від споживачів, а події — прості дані без доменних об'єктів.
        List<String> producer = importsOf(application.resolve("com/internetshop/application/ordering"));
        check(producer.stream().noneMatch(i -> i.contains(".notifications.") || i.contains(".analytics.")
                        || i.contains("InProcessEventBus")),
                "Ordering (event producer) does not depend on handlers or the bus implementation");
        check(importsOf(application.resolve("com/internetshop/application/ordering/events")).stream()
                        .allMatch(i -> i.startsWith("java.") || i.startsWith("com.internetshop.application.shared.events.")),
                "Events are plain data: no domain objects or services inside");
        List<String> consumers = new ArrayList<>(importsOf(application.resolve("com/internetshop/application/notifications")));
        consumers.addAll(importsOf(application.resolve("com/internetshop/application/analytics")));
        check(consumers.stream().noneMatch(i -> i.contains("OrderService") || i.contains("OrderStore")),
                "Event handlers do not call Ordering services or stores");
    }

    private static List<String> importsOf(Path dir) throws IOException {
        List<String> imports = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(f)) {
                    String t = line.trim();
                    if (t.startsWith("import ")) imports.add(t.replace("import static ", "").replace("import ", ""));
                }
            }
        }
        return imports;
    }

    // ---------------- HTTP (localhost) ----------------

    private static void http(Path dir) throws Exception {
        HttpServer server = InternetShopApp.start(0, dir); // 0 — будь-який вільний порт
        String base = "http://localhost:" + server.getAddress().getPort();
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> page = send(client, "GET", base + "/", null, null);
            check(page.statusCode() == 200 && page.body().contains("InternetShop"), "GET / returns the demo page");

            HttpResponse<String> list = send(client, "GET", base + "/products", null, null);
            check(list.statusCode() == 200 && list.body().contains("\"price\""), "GET /products returns seeded catalog");

            HttpResponse<String> product = send(client, "POST", base + "/products", "application/json",
                    "{\"name\":\"<script>Тест</script>\",\"description\":\"\",\"price\":10.5}");
            check(product.statusCode() == 201 && product.body().contains("\\u003cscript\\u003e"),
                    "POST /products -> 201, HTML characters escaped in JSON");
            String productId = field(product.body(), "id");

            HttpResponse<String> order = send(client, "POST", base + "/orders", "application/json",
                    "{\"customerId\":\"" + UUID.randomUUID() + "\"}");
            String orderId = field(order.body(), "id");
            check(order.statusCode() == 201 && order.body().contains("\"DRAFT\""), "POST /orders -> 201 DRAFT");

            HttpResponse<String> tooMany = send(client, "POST", base + "/orders/" + orderId + "/items", "application/json",
                    "{\"productId\":\"" + productId + "\",\"quantity\":100}");
            check(tooMany.statusCode() == 400 && tooMany.body().contains("від 1 до 99"), "Business rule violation -> 400 with message");

            HttpResponse<String> added = send(client, "POST", base + "/orders/" + orderId + "/items", "application/json",
                    "{\"productId\":\"" + productId + "\",\"quantity\":2}");
            check(added.statusCode() == 200 && added.body().contains("\"total\":21.00"), "Item added via HTTP, total 21.00");

            HttpResponse<String> placed = send(client, "POST", base + "/orders/" + orderId + "/place", "application/json", "{}");
            check(placed.statusCode() == 200 && placed.body().contains("\"PLACED\""), "Order placed via HTTP");

            HttpResponse<String> notifications = send(client, "GET", base + "/notifications", null, null);
            check(notifications.statusCode() == 200 && notifications.body().contains(orderId)
                    && notifications.body().contains("21.00"), "GET /notifications shows reaction to OrderPlaced");
            HttpResponse<String> statistics = send(client, "GET", base + "/statistics", null, null);
            check(statistics.statusCode() == 200 && statistics.body().contains("\"ordersCreated\":1")
                    && statistics.body().contains("\"ordersPlaced\":1") && statistics.body().contains("\"revenue\":21.00"),
                    "GET /statistics shows reactions to OrderCreated and OrderPlaced");
            check(send(client, "POST", base + "/statistics", "application/json", "{}").statusCode() == 405,
                    "Statistics are read-only over HTTP");

            check(send(client, "GET", base + "/orders/" + UUID.randomUUID(), null, null).statusCode() == 404, "Unknown order -> 404");
            check(send(client, "GET", base + "/orders/not-a-uuid", null, null).statusCode() == 400, "Malformed id -> 400");
            check(send(client, "POST", base + "/orders", "application/json", "{bad json").statusCode() == 400, "Malformed JSON -> 400");
            check(send(client, "POST", base + "/orders", "text/plain", "{}").statusCode() == 415, "POST without JSON content type -> 415");
            check(send(client, "DELETE", base + "/orders", null, null).statusCode() == 405, "Unsupported method -> 405");
            HttpResponse<String> deleted = send(client, "DELETE", base + "/products/" + productId, null, null);
            check(deleted.statusCode() == 204 && deleted.body().isEmpty(), "DELETE /products/{id} -> 204");
            check(send(client, "GET", base + "/products/" + productId, null, null).statusCode() == 404, "Deleted product -> 404");
            check(send(client, "DELETE", base + "/products/" + productId, null, null).statusCode() == 404, "Repeated delete -> 404");
            HttpResponse<String> big = send(client, "POST", base + "/products", "application/json", "{\"name\":\"" + "x".repeat(20000) + "\"}");
            check(big.statusCode() == 413, "Oversized body -> 413");
        } finally {
            server.stop(0);
        }
    }

    private static HttpResponse<String> send(HttpClient client, String method, String url, String contentType, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url));
        if (contentType != null) b.header("Content-Type", contentType);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Достатньо для перевірок: дістає перше значення "key":"value". */
    private static String field(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    // ---------------- допоміжне ----------------

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError("FAIL: " + name);
        System.out.println("PASS: " + name);
        passed++;
    }

    private static void reject(Runnable action, String name) {
        expect(DomainRuleException.class, action, name);
    }

    private static void expect(Class<? extends Exception> type, Runnable action, String name) {
        try {
            action.run();
        } catch (Exception e) {
            if (type.isInstance(e)) { check(true, name); return; }
            throw new AssertionError("FAIL: " + name + ": unexpected " + e, e);
        }
        throw new AssertionError("FAIL: " + name + ": operation was accepted");
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
