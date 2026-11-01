package com.internetshop.checks;

import com.internetshop.application.analytics.OrderCreatedStatisticsHandler;
import com.internetshop.application.analytics.OrderPlacedStatisticsHandler;
import com.internetshop.application.analytics.SalesReport;
import com.internetshop.application.analytics.SalesStatistics;
import com.internetshop.application.catalog.CatalogService;
import com.internetshop.application.catalog.CatalogUseCases;
import com.internetshop.application.catalog.ProductDtoMapper;
import com.internetshop.application.catalog.ProductMapper;
import com.internetshop.application.catalog.ProductQueries;
import com.internetshop.application.catalog.ProductRepository;
import com.internetshop.application.catalog.ProductNotFoundException;
import com.internetshop.application.catalog.dto.CreateProductRequest;
import com.internetshop.application.catalog.dto.CreatedProductDto;
import com.internetshop.application.catalog.dto.ProductDto;
import com.internetshop.application.notifications.Notification;
import com.internetshop.application.notifications.NotificationFeed;
import com.internetshop.application.notifications.NotificationSender;
import com.internetshop.application.notifications.OrderPlacedNotificationHandler;
import com.internetshop.application.ordering.CatalogProduct;
import com.internetshop.application.ordering.OrderDtoMapper;
import com.internetshop.application.ordering.OrderMapper;
import com.internetshop.application.ordering.OrderNotFoundException;
import com.internetshop.application.ordering.OrderService;
import com.internetshop.application.ordering.OrderRepository;
import com.internetshop.application.ordering.OrderUseCases;
import com.internetshop.application.ordering.ProductNotInCatalogException;
import com.internetshop.application.ordering.dto.AddOrderItemRequest;
import com.internetshop.application.ordering.dto.CreateOrderRequest;
import com.internetshop.application.ordering.dto.CreatedOrderDto;
import com.internetshop.application.ordering.dto.OrderDetailsDto;
import com.internetshop.application.ordering.dto.OrderLineDto;
import com.internetshop.application.ordering.dto.PlacedOrderDto;
import com.internetshop.application.ordering.events.OrderCreated;
import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.application.shared.events.Event;
import com.internetshop.application.shared.ConcurrentChangeException;
import com.internetshop.application.shared.NotFoundException;
import com.internetshop.application.shared.TimeProvider;
import com.internetshop.application.shared.UnitOfWork;
import com.internetshop.application.shared.events.EventBus;
import com.internetshop.application.shared.validation.AbstractValidator;
import com.internetshop.application.shared.validation.ValidationException;
import com.internetshop.application.shared.validation.ValidationFailure;
import com.internetshop.application.shared.validation.ValidationResult;
import com.internetshop.application.catalog.validation.CreateProductRequestValidator;
import com.internetshop.application.ordering.validation.AddOrderItemRequestValidator;
import com.internetshop.application.ordering.validation.CreateOrderRequestValidator;
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
import com.internetshop.infrastructure.persistence.FileDataContext;
import com.internetshop.infrastructure.persistence.FileOrderRepository;
import com.internetshop.infrastructure.persistence.FileProductRepository;
import com.internetshop.infrastructure.persistence.FileUnitOfWork;
import com.internetshop.infrastructure.time.SystemTimeProvider;
import com.internetshop.webapi.InternetShopApp;
import com.internetshop.webapi.di.DependencyResolutionException;
import com.internetshop.webapi.di.Lifetime;
import com.internetshop.webapi.di.ServiceCollection;
import com.internetshop.webapi.di.ServiceProvider;
import com.internetshop.webapi.di.ServiceScope;
import com.internetshop.webapi.endpoints.NotificationEndpoints;
import com.internetshop.webapi.endpoints.OrderEndpoints;
import com.internetshop.webapi.endpoints.ProductEndpoints;
import com.internetshop.webapi.endpoints.StatisticsEndpoints;
import com.internetshop.webapi.http.ErrorMapper;
import com.internetshop.webapi.http.Json;
import com.internetshop.webapi.http.Router;
import com.internetshop.webapi.validation.AutoValidation;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Консольні перевірки (аналог CourseDemo.Checks): Domain -> Application + Infrastructure ->
 * події та обробники (ПР2) -> принципи SOLID (ПР3) -> Dependency Injection (ПР4) -> Repository і Unit of Work (ПР5) ->
 * DTO і мапінг (ПР6) -> валідація вхідних даних (ПР8) -> межі шарів і контекстів -> архітектурний стиль
 * «модульний моноліт» (ПР7) -> HTTP.
 * Використовують окрему тимчасову папку даних.
 * Запуск: mvn verify (або з кореня проєкту); при першій невдалій перевірці — виняток і помилка збірки.
 */
public final class Checks {

    private static int passed;

    /** Справжнє джерело часу для сценаріїв, де конкретний час неважливий. */
    private static final TimeProvider CLOCK = new SystemTimeProvider();
    /** Мапери без стану: у перевірках, де сервіси збираються вручну, передаються так само через конструктор. */
    private static final ProductMapper PRODUCT_MAPPER = new ProductDtoMapper();
    private static final OrderMapper ORDER_MAPPER = new OrderDtoMapper();

    /** Сценарій каталогу поза контейнером («один запит»): свій Unit of Work і репозиторій над спільним контекстом даних. */
    private static CatalogService catalogService(FileDataContext db) {
        FileUnitOfWork unitOfWork = new FileUnitOfWork(db);
        return new CatalogService(new FileProductRepository(db, unitOfWork), unitOfWork, PRODUCT_MAPPER);
    }

    /** Сценарії замовлень поза контейнером: так само власний Unit of Work і репозиторій. */
    private static OrderService orderService(FileDataContext db, ProductQueries catalog, EventBus bus, TimeProvider time) {
        FileUnitOfWork unitOfWork = new FileUnitOfWork(db);
        return new OrderService(new FileOrderRepository(db, unitOfWork), unitOfWork, new CatalogProductAdapter(catalog),
                bus, time, ORDER_MAPPER);
    }

    public static void main(String[] args) throws Exception {
        domainCatalog();
        domainOrdering();
        Path tmp = Files.createTempDirectory("internetshop-checks-");
        try {
            persistenceAndContexts(tmp.resolve("store"));
            events(tmp.resolve("events"));
            solid(tmp.resolve("solid"));
            dependencyInjection(tmp.resolve("di"));
            repositoryAndUnitOfWork(tmp.resolve("uow"));
            dataTransferObjects(tmp.resolve("dto"));
            validation(tmp.resolve("validation"));
            architecture(Path.of(args.length > 0 ? args[0] : "."));
            architecturalStyle(Path.of(args.length > 0 ? args[0] : "."));
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
        FileDataContext db = new FileDataContext(dir);
        CatalogService catalog = catalogService(db);
        OrderService orders = orderService(db, catalog, new InProcessEventBus(), CLOCK);
        UUID productId = catalog.create(new CreateProductRequest("Ноутбук", "14 дюймів", new BigDecimal("30000"))).id();
        UUID orderId = orders.create(new CreateOrderRequest(UUID.randomUUID())).id();
        orders.addItem(orderId, new AddOrderItemRequest(productId, 1));
        orders.place(orderId);

        // Новий контекст даних = «перезапуск застосунку»: дані читаються з файлів.
        FileDataContext db2 = new FileDataContext(dir);
        CatalogService catalog2 = catalogService(db2);
        OrderService orders2 = orderService(db2, catalog2, new InProcessEventBus(), CLOCK);
        OrderDetailsDto saved = orders2.get(orderId);
        check(catalog2.get(productId).name().equals("Ноутбук")
                && saved.status().equals("PLACED") && saved.lines().size() == 1
                && saved.lines().get(0).productName().equals("Ноутбук")
                && saved.total().compareTo(new BigDecimal("30000")) == 0, "Data survives restart (new store instances)");
        expect(DomainRuleException.class, () -> orders2.addItem(orderId, new AddOrderItemRequest(productId, 1)), "Domain rules hold after rehydration");
        expect(OrderNotFoundException.class, () -> orders2.get(UUID.randomUUID()), "Missing order reported");

        UUID draftId = orders2.create(new CreateOrderRequest(UUID.randomUUID())).id();
        expect(ProductNotInCatalogException.class, () -> orders2.addItem(draftId, new AddOrderItemRequest(UUID.randomUUID(), 1)),
                "Ordering asks Catalog through ProductCatalog contract: unknown product rejected");
        expect(DomainRuleException.class, () -> orders2.create(new CreateOrderRequest(null)), "Missing customer id rejected by domain");

        // Імітуємо два запити (кожен зі своїм Unit of Work), які одночасно прочитали ту саму чернетку.
        FileUnitOfWork request1 = new FileUnitOfWork(db2);
        FileUnitOfWork request2 = new FileUnitOfWork(db2);
        OrderRepository repository1 = new FileOrderRepository(db2, request1);
        OrderRepository repository2 = new FileOrderRepository(db2, request2);
        Order first = repository1.findById(draftId).orElseThrow();
        Order second = repository2.findById(draftId).orElseThrow();
        first.addItem(productId, "Ноутбук", Money.of("30000"), new Quantity(1));
        second.addItem(productId, "Ноутбук", Money.of("30000"), new Quantity(2));
        repository1.update(first);
        request1.saveChanges();
        repository2.update(second);
        expect(ConcurrentChangeException.class, request2::saveChanges, "Concurrent update rejected (optimistic version)");
        check(orders2.get(draftId).lines().get(0).quantity() == 1, "Failed concurrent update leaves first result intact");

        // Видалення товару з каталогу (Catalog) не змінює вже сформовані замовлення (Ordering).
        catalog2.delete(productId);
        expect(ProductNotFoundException.class, () -> catalog2.get(productId), "Deleted product is no longer in catalog");
        check(catalogService(new FileDataContext(dir)).list().stream().noneMatch(p -> p.id().equals(productId)),
                "Product deletion survives restart");
        check(orders2.get(orderId).lines().get(0).productName().equals("Ноутбук"),
                "Existing order keeps its snapshot after product deletion");
        UUID anotherDraft = orders2.create(new CreateOrderRequest(UUID.randomUUID())).id();
        expect(ProductNotInCatalogException.class, () -> orders2.addItem(anotherDraft, new AddOrderItemRequest(productId, 1)),
                "Deleted product cannot be added to a new order");
        expect(ProductNotFoundException.class, () -> catalog2.delete(productId), "Deleting a missing product reported");
    }

    // ---------------- ПР2: події та обробники (Event-Driven) ----------------

    private static void events(Path dir) {
        FileDataContext db = new FileDataContext(dir);
        CatalogService catalog = catalogService(db);
        InProcessEventBus bus = new InProcessEventBus();
        List<Event> published = new ArrayList<>();
        bus.subscribe(Event.class, published::add); // «спостерігач» за всіма подіями — лише для перевірок
        OrderService orders = orderService(db, catalog, bus, CLOCK);

        UUID productId = catalog.create(new CreateProductRequest("Миша", "", new BigDecimal("100.50"))).id();
        UUID customerId = UUID.randomUUID();
        UUID orderId = orders.create(new CreateOrderRequest(customerId)).id();
        check(published.size() == 1 && published.get(0) instanceof OrderCreated c && c.orderId().equals(orderId),
                "Creating an order publishes OrderCreated");

        orders.addItem(orderId, new AddOrderItemRequest(productId, 3));
        check(published.size() == 1, "Adding an item publishes no event");

        UUID emptyId = orders.create(new CreateOrderRequest(customerId)).id();
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
        OrderService orders2 = orderService(db, catalog, bus2, CLOCK);
        UUID order2 = orders2.create(new CreateOrderRequest(customerId)).id();
        orders2.addItem(order2, new AddOrderItemRequest(productId, 1));
        Logger busLog = Logger.getLogger(InProcessEventBus.class.getName());
        Level previous = busLog.getLevel();
        busLog.setLevel(Level.OFF); // очікуваний збій обробника не засмічує вивід перевірок
        PlacedOrderDto placed;
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

        for (int i = 0; i < 60; i++) outbox2.send(n);
        check(outbox2.findRecent().size() == 50, "Notification outbox keeps a bounded number of messages");
    }

    // ---------------- ПР3: принципи SOLID після рефакторингу ----------------

    private static void solid(Path dir) throws Exception {
        // SRP: перетворення на DTO — окремий клас; OrderService лише організовує сценарії.
        Order order = new Order(new CustomerId(UUID.randomUUID()));
        UUID mouse = UUID.randomUUID();
        order.addItem(mouse, "Миша", Money.of("100.50"), new Quantity(2));
        OrderDetailsDto dto = ORDER_MAPPER.toDetails(order);
        check(dto.id().equals(order.getId()) && dto.status().equals("DRAFT") && dto.lines().size() == 1
                        && dto.lines().get(0).productId().equals(mouse) && dto.lines().get(0).lineTotal().compareTo(new BigDecimal("201")) == 0
                        && dto.total().compareTo(new BigDecimal("201")) == 0,
                "SRP: OrderMapper converts the aggregate to DTO");
        check(Arrays.stream(OrderService.class.getDeclaredMethods()).noneMatch(m -> m.getName().equals("toDto")),
                "SRP: OrderService no longer contains DTO mapping");

        // DIP: усі залежності OrderService — абстракції, які передаються через конструктор.
        Constructor<?>[] ctors = OrderService.class.getConstructors();
        check(ctors.length == 1 && ctors[0].getParameterCount() == 6
                        && Arrays.stream(ctors[0].getParameterTypes()).allMatch(Class::isInterface),
                "DIP: OrderService depends only on interfaces injected through its constructor");

        // DIP: час події задає підставлена реалізація TimeProvider, а не системний годинник.
        Instant fixed = Instant.parse("2026-01-01T10:00:00Z");
        FileDataContext db = new FileDataContext(dir);
        CatalogService catalog = catalogService(db);
        UUID productId = catalog.create(new CreateProductRequest("Кабель", "", new BigDecimal("199"))).id();
        InProcessEventBus bus = new InProcessEventBus();
        List<Event> published = new ArrayList<>();
        bus.subscribe(Event.class, published::add);
        OrderService orders = orderService(db, catalog, bus, () -> fixed);
        UUID orderId = orders.create(new CreateOrderRequest(UUID.randomUUID())).id();
        orders.addItem(orderId, new AddOrderItemRequest(productId, 1));
        orders.place(orderId);
        check(published.size() == 2 && published.stream().allMatch(e -> e.occurredAt().equals(fixed)),
                "DIP: event time comes from the injected TimeProvider (testable without the system clock)");

        // DIP + ISP: адаптер Ordering → Catalog працює з будь-якою реалізацією вузького ProductQueries.
        check(Arrays.equals(CatalogProductAdapter.class.getConstructors()[0].getParameterTypes(),
                        new Class<?>[]{ProductQueries.class}) && ProductQueries.class.isInterface(),
                "DIP + ISP: CatalogProductAdapter depends on read-only ProductQueries, not on CatalogService");
        UUID knownId = UUID.randomUUID();
        ProductQueries stub = new ProductQueries() {
            @Override public List<ProductDto> list() { return List.of(); }
            @Override public ProductDto get(UUID id) {
                if (!knownId.equals(id)) throw new com.internetshop.application.catalog.ProductNotFoundException();
                return new ProductDto(knownId, "Тестовий товар", "", new BigDecimal("5.00"));
            }
        };
        CatalogProductAdapter adapter = new CatalogProductAdapter(stub);
        Optional<CatalogProduct> found = adapter.findProduct(knownId);
        check(found.isPresent() && found.get().name().equals("Тестовий товар") && adapter.findProduct(UUID.randomUUID()).isEmpty(),
                "DIP: adapter works with a stub catalog (no files, no CatalogService)");

        // ISP: обробник сповіщень лише надсилає, HTTP-маршрут лише читає.
        check(Arrays.equals(OrderPlacedNotificationHandler.class.getConstructors()[0].getParameterTypes(),
                        new Class<?>[]{NotificationSender.class}) && NotificationSender.class.getMethods().length == 1,
                "ISP: notification handler gets only NotificationSender (cannot read notifications)");
        check(Arrays.equals(NotificationEndpoints.class.getConstructors()[0].getParameterTypes(),
                        new Class<?>[]{NotificationFeed.class}) && NotificationFeed.class.getMethods().length == 1,
                "ISP: GET /notifications gets only read-only NotificationFeed");
        InMemoryNotificationOutbox outbox = new InMemoryNotificationOutbox();
        NotificationSender sender = outbox;
        NotificationFeed feed = outbox;
        sender.send(new Notification(UUID.randomUUID(), orderId, "test", fixed));
        check(feed.findRecent().size() == 1, "ISP: one implementation serves both narrow interfaces");

        // LSP: правило для базового NotFoundException коректно працює для всіх підтипів.
        ErrorMapper errors = new ErrorMapper().map(NotFoundException.class, 404);
        check(errors.statusFor(new com.internetshop.application.catalog.ProductNotFoundException()).orElse(0) == 404
                        && errors.statusFor(new OrderNotFoundException()).orElse(0) == 404
                        && errors.statusFor(new IllegalStateException()).isEmpty(),
                "LSP: subtypes of NotFoundException are handled by the base-type rule");

        // OCP: новий тип помилки підключається новим правилом, без жодних змін у Router.
        final class OutOfStockException extends RuntimeException {
            OutOfStockException() { super("Немає на складі."); }
        }
        Router router = new Router(new ErrorMapper().map(OutOfStockException.class, 422), new ServiceCollection().build())
                .route("GET", "/new-rule", req -> { throw new OutOfStockException(); })
                .route("GET", "/unexpected", req -> { throw new IllegalStateException("internal-secret-detail"); });
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", router);
        server.start();
        Logger routerLog = Logger.getLogger(Router.class.getName());
        Level previous = routerLog.getLevel();
        routerLog.setLevel(Level.OFF); // очікувана «неочікувана» помилка не засмічує вивід перевірок
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> mapped = send(client, "GET", base + "/new-rule", null, null);
            check(mapped.statusCode() == 422 && mapped.body().contains("Немає на складі."),
                    "OCP: new exception type mapped to HTTP status without changing Router");
            HttpResponse<String> unexpected = send(client, "GET", base + "/unexpected", null, null);
            check(unexpected.statusCode() == 500 && !unexpected.body().contains("internal-secret-detail"),
                    "Unmapped error -> 500 without internal details");
        } finally {
            routerLog.setLevel(previous);
            server.stop(0);
        }
    }

    // ---------------- ПР4: Dependency Injection і життєві цикли ----------------

    /** Стан «одного запиту» для перевірок області (scope): рахує створення і закриття. */
    public static final class RequestState implements AutoCloseable {
        static final AtomicInteger CREATED = new AtomicInteger();
        static final AtomicInteger CLOSED = new AtomicInteger();
        public RequestState() { CREATED.incrementAndGet(); }
        @Override public void close() { CLOSED.incrementAndGet(); }
    }

    /** Ще один компонент запиту, який теж залежить від RequestState. */
    public static final class RequestStateUser {
        final RequestState state;
        public RequestStateUser(RequestState state) { this.state = state; }
    }

    /** Ендпоінт для перевірки: отримує обидві залежності через конструктор. */
    public static final class ProbeEndpoint {
        final RequestState state;
        final RequestStateUser user;
        public ProbeEndpoint(RequestState state, RequestStateUser user) { this.state = state; this.user = user; }
    }

    public static final class CycleA { public CycleA(CycleB b) { } }

    public static final class CycleB { public CycleB(CycleA a) { } }

    private static void dependencyInjection(Path dir) throws Exception {
        ServiceProvider services = InternetShopApp.configureServices(dir);

        // Життєві цикли, налаштовані в точці складання, — як у прикладах методички.
        check(services.lifetimeOf(OrderMapper.class) == Lifetime.TRANSIENT && services.lifetimeOf(ProductMapper.class) == Lifetime.TRANSIENT
                        && services.lifetimeOf(OrderUseCases.class) == Lifetime.SCOPED && services.lifetimeOf(CatalogUseCases.class) == Lifetime.SCOPED
                        && services.lifetimeOf(EventBus.class) == Lifetime.SINGLETON && services.lifetimeOf(FileDataContext.class) == Lifetime.SINGLETON,
                "DI: mappers Transient, use cases Scoped, event bus and data context Singleton");

        try (ServiceScope request1 = services.createScope(); ServiceScope request2 = services.createScope()) {
            check(request1.get(OrderMapper.class) != request1.get(OrderMapper.class),
                    "Transient: a new instance on every request to the container");
            check(request1.get(OrderUseCases.class) == request1.get(OrderUseCases.class)
                            && request1.get(OrderUseCases.class) != request2.get(OrderUseCases.class),
                    "Scoped: one instance within a scope, different instances in different scopes");
            check(request1.get(ProductQueries.class) == request1.get(CatalogUseCases.class),
                    "Scoped: all components of one request share the same CatalogService");
            check(request1.get(EventBus.class) == request2.get(EventBus.class) && request1.get(EventBus.class) == services.get(EventBus.class)
                            && services.get(EventBus.class) == services.get(InProcessEventBus.class),
                    "Singleton: one event bus for the whole application (publish and subscribe see the same bus)");
            check(services.get(NotificationSender.class) == services.get(NotificationFeed.class)
                            && services.get(SalesReport.class) == services.get(SalesStatistics.class),
                    "Singleton: one shared instance behind several narrow interfaces");
            check(request1.get(ProductEndpoints.class) != null && request1.get(OrderEndpoints.class) != null
                            && request1.get(NotificationEndpoints.class) != null && request1.get(StatisticsEndpoints.class) != null,
                    "Container configuration is complete: every endpoint is created with all its dependencies");
        }

        // Контейнер сам будує граф: сценарій, отриманий з області, працює з реальними залежностями.
        try (ServiceScope scope = services.createScope()) {
            UUID productId = scope.get(CatalogUseCases.class).create(new CreateProductRequest("Кабель", "", new BigDecimal("199"))).id();
            OrderUseCases orders = scope.get(OrderUseCases.class);
            UUID orderId = orders.create(new CreateOrderRequest(UUID.randomUUID())).id();
            check(orders.addItem(orderId, new AddOrderItemRequest(productId, 2)).total().compareTo(new BigDecimal("398")) == 0,
                    "Use case resolved from the container works end to end");
        }

        // Правила життєвого циклу перевіряє контейнер.
        expect(DependencyResolutionException.class, () -> services.get(OrderUseCases.class),
                "Scoped dependency cannot be taken from the root container (outside a request)");
        ServiceProvider captive = new ServiceCollection()
                .addScoped(SalesStatistics.class, SalesStatistics.class)
                .addSingleton(OrderCreatedStatisticsHandler.class, OrderCreatedStatisticsHandler.class)
                .build();
        try {
            captive.get(OrderCreatedStatisticsHandler.class);
            throw new AssertionError("FAIL: singleton received a scoped dependency");
        } catch (DependencyResolutionException e) {
            check(e.getMessage().contains("SINGLETON OrderCreatedStatisticsHandler") && e.getMessage().contains("SCOPED SalesStatistics"),
                    "Singleton depending on Scoped is rejected with a clear message");
        }
        ServiceProvider incomplete = new ServiceCollection().addScoped(OrderUseCases.class, OrderService.class).build();
        try (ServiceScope scope = incomplete.createScope()) {
            expect(DependencyResolutionException.class, () -> scope.get(OrderUseCases.class),
                    "Missing registration reported instead of a hidden new inside the class");
        }
        ServiceProvider cycle = new ServiceCollection()
                .addTransient(CycleA.class, CycleA.class).addTransient(CycleB.class, CycleB.class).build();
        expect(DependencyResolutionException.class, () -> cycle.get(CycleA.class), "Circular dependency detected");
        expect(IllegalArgumentException.class, () -> new ServiceCollection()
                        .addTransient(OrderMapper.class, OrderDtoMapper.class).addTransient(OrderMapper.class, OrderDtoMapper.class),
                "Duplicate registration rejected");

        // Область запиту: Router створює її на кожен HTTP-запит і закриває після відповіді.
        ServiceProvider probe = new ServiceCollection()
                .addScoped(RequestState.class, RequestState.class)
                .addScoped(RequestStateUser.class, RequestStateUser.class)
                .addScoped(ProbeEndpoint.class, ProbeEndpoint.class)
                .build();
        Router router = new Router(new ErrorMapper(), probe)
                .route("GET", "/probe", ProbeEndpoint.class, (endpoint, req) ->
                        Router.Response.ok(Map.of("same", endpoint.state == endpoint.user.state)));
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", router);
        server.start();
        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/probe";
            HttpClient client = HttpClient.newHttpClient();
            int createdBefore = RequestState.CREATED.get();
            int closedBefore = RequestState.CLOSED.get();
            HttpResponse<String> first = send(client, "GET", url, null, null);
            HttpResponse<String> second = send(client, "GET", url, null, null);
            check(first.body().contains("\"same\":true") && second.body().contains("\"same\":true"),
                    "HTTP: within one request all components get the same scoped instance");
            check(RequestState.CREATED.get() - createdBefore == 2 && RequestState.CLOSED.get() - closedBefore == 2,
                    "HTTP: each request gets its own scope, closed after the response");
        } finally {
            server.stop(0);
        }

        // Ендпоінти і сценарії залежать лише від абстракцій, переданих через конструктор.
        List<Class<?>> components = List.of(ProductEndpoints.class, OrderEndpoints.class, NotificationEndpoints.class,
                StatisticsEndpoints.class, CatalogService.class, OrderService.class, CatalogProductAdapter.class);
        check(components.stream().allMatch(c -> c.getConstructors().length == 1
                        && Arrays.stream(c.getConstructors()[0].getParameterTypes()).allMatch(Class::isInterface)),
                "Endpoints and use cases depend only on interfaces injected through the constructor");
    }

    // ---------------- ПР5: Repository та Unit of Work ----------------

    private static void repositoryAndUnitOfWork(Path dir) throws IOException {
        // Абстракції — в Application, реалізації — в Infrastructure; сценарії залежать лише від абстракцій.
        check(OrderRepository.class.isInterface() && ProductRepository.class.isInterface() && UnitOfWork.class.isInterface()
                        && OrderRepository.class.getPackageName().startsWith("com.internetshop.application")
                        && UnitOfWork.class.getPackageName().startsWith("com.internetshop.application")
                        && OrderRepository.class.isAssignableFrom(FileOrderRepository.class)
                        && ProductRepository.class.isAssignableFrom(FileProductRepository.class)
                        && UnitOfWork.class.isAssignableFrom(FileUnitOfWork.class)
                        && FileOrderRepository.class.getPackageName().startsWith("com.internetshop.infrastructure"),
                "Repository and Unit of Work interfaces live in Application, implementations in Infrastructure");
        check(List.of(OrderService.class.getConstructors()[0].getParameterTypes()).containsAll(List.of(OrderRepository.class, UnitOfWork.class))
                        && Arrays.equals(CatalogService.class.getConstructors()[0].getParameterTypes(),
                        new Class<?>[]{ProductRepository.class, UnitOfWork.class, ProductMapper.class}),
                "Application services work only with Repository and Unit of Work abstractions");

        // Репозиторій сам не зберігає: зміни з'являються у сховищі лише після saveChanges.
        FileDataContext db = new FileDataContext(dir);
        FileUnitOfWork unitOfWork = new FileUnitOfWork(db);
        ProductRepository products = new FileProductRepository(db, unitOfWork);
        Product mouse = new Product(new ProductName("Миша"), "", Money.of("100"));
        products.add(mouse);
        check(products.findById(mouse.getId()).isEmpty() && productsAfterRestart(dir).findAll().isEmpty(),
                "Repository only registers changes: nothing is saved before saveChanges");
        unitOfWork.saveChanges();
        check(productsAfterRestart(dir).findById(mouse.getId())
                        .map(p -> p.getName().value().equals("Миша")).orElse(false),
                "Unit of Work: saveChanges persists registered changes (visible after restart)");

        // Кінець області запиту без saveChanges = відкат.
        Product cable = new Product(new ProductName("Кабель"), "", Money.of("199"));
        products.add(cable);
        unitOfWork.close();
        unitOfWork.saveChanges();
        check(products.findById(cable.getId()).isEmpty(), "Unit of Work: unsaved changes are discarded when the request scope closes");

        // Атомарність: дві зміни однієї операції — або обидві, або жодної.
        OrderRepository orders = new FileOrderRepository(db, unitOfWork);
        Order order = new Order(new CustomerId(UUID.randomUUID()));
        orders.add(order);
        unitOfWork.saveChanges();
        Order stale = orders.findById(order.getId()).orElseThrow();
        Order fresh = orders.findById(order.getId()).orElseThrow();
        fresh.addItem(mouse.getId(), "Миша", Money.of("100"), new Quantity(1));
        orders.update(fresh);
        unitOfWork.saveChanges();                         // інший запит устиг змінити замовлення
        Product keyboard = new Product(new ProductName("Клавіатура"), "", Money.of("900"));
        products.add(keyboard);                           // зміна 1 — коректна
        stale.addItem(mouse.getId(), "Миша", Money.of("100"), new Quantity(5));
        orders.update(stale);                             // зміна 2 — застаріла версія
        expect(ConcurrentChangeException.class, unitOfWork::saveChanges, "Unit of Work: conflicting change fails the whole saveChanges");
        check(products.findById(keyboard.getId()).isEmpty() && productsAfterRestart(dir).findById(keyboard.getId()).isEmpty()
                        && orders.findById(order.getId()).orElseThrow().getLines().get(0).getQuantity().value() == 1,
                "Unit of Work: atomic - when one change fails, no change of the operation is saved");
        unitOfWork.saveChanges();
        check(products.findById(keyboard.getId()).isEmpty(), "Unit of Work: failed changes are dropped, not re-applied by the next saveChanges");

        // Атомарність між файлами: якщо orders.db записати не вдалося, уже записаний products.db повертається.
        Path ordersFile = dir.resolve("orders.db");
        Path backup = dir.resolve("orders.backup");
        Files.move(ordersFile, backup);
        Files.createDirectories(ordersFile.resolve("blocked")); // файл замовлень неможливо перезаписати
        products.add(keyboard);
        orders.add(new Order(new CustomerId(UUID.randomUUID())));
        boolean failed = false;
        try {
            unitOfWork.saveChanges();
        } catch (RuntimeException e) {
            failed = true;
        }
        Files.delete(ordersFile.resolve("blocked"));
        Files.delete(ordersFile);
        Files.move(backup, ordersFile);
        check(failed && products.findById(keyboard.getId()).isEmpty()
                        && productsAfterRestart(dir).findById(keyboard.getId()).isEmpty(),
                "Unit of Work: write failure of one file rolls back the other (no half-saved operation)");

        // Сценарій: кожна бізнес-операція закінчується одним saveChanges, читання нічого не зберігає.
        AtomicInteger saves = new AtomicInteger();
        FileUnitOfWork real = new FileUnitOfWork(db);
        UnitOfWork counting = () -> { saves.incrementAndGet(); real.saveChanges(); };
        OrderService service = new OrderService(new FileOrderRepository(db, real), counting,
                new CatalogProductAdapter(catalogService(db)), new InProcessEventBus(), CLOCK, ORDER_MAPPER);
        UUID orderId = service.create(new CreateOrderRequest(UUID.randomUUID())).id();
        service.addItem(orderId, new AddOrderItemRequest(mouse.getId(), 2));
        service.place(orderId);
        service.list();
        service.get(orderId);
        check(saves.get() == 3 && new FileOrderRepository(new FileDataContext(dir), new FileUnitOfWork(db)).findById(orderId)
                        .map(o -> o.getStatus() == OrderStatus.PLACED && o.getLines().size() == 1).orElse(false),
                "Business operations still work: each one is saved by exactly one saveChanges, reads save nothing");
    }

    // ---------------- ПР6: DTO та мапінг між шарами ----------------

    /** Усі DTO прикладного шару: вхідні (запити) і вихідні (результати операцій). */
    private static final List<Class<?>> DTOS = List.of(CreateOrderRequest.class, AddOrderItemRequest.class,
            OrderDetailsDto.class, OrderLineDto.class, CreatedOrderDto.class, PlacedOrderDto.class,
            CreateProductRequest.class, ProductDto.class, CreatedProductDto.class);

    private static void dataTransferObjects(Path dir) {
        // DTO — прості структури даних: незмінні record, лише прості типи, без доменних класів.
        check(DTOS.stream().allMatch(c -> c.isRecord() && Arrays.stream(c.getRecordComponents())
                        .map(RecordComponent::getGenericType).map(Type::getTypeName)
                        .noneMatch(t -> t.contains("com.internetshop.domain"))),
                "DTO are plain records without domain types (Money, Quantity, CustomerId, Order...)");
        // DTO без бізнес-логіки: окрім аксесорів полів — лише згенеровані equals/hashCode/toString.
        Set<String> generated = Set.of("equals", "hashCode", "toString");
        check(DTOS.stream().allMatch(c -> Arrays.stream(c.getDeclaredMethods())
                        .filter(m -> !m.isSynthetic() && !generated.contains(m.getName()))
                        .allMatch(m -> m.getParameterCount() == 0 && Arrays.stream(c.getRecordComponents())
                                .anyMatch(rc -> rc.getName().equals(m.getName())))),
                "DTO contain no business logic (only field accessors)");

        // Межа прикладного шару: у сигнатурах сценаріїв лише DTO і Id, жодного доменного класу.
        List<Method> boundary = new ArrayList<>();
        for (Class<?> c : List.of(OrderUseCases.class, CatalogUseCases.class, ProductQueries.class))
            boundary.addAll(Arrays.asList(c.getMethods()));
        check(boundary.stream().allMatch(m -> !m.getGenericReturnType().getTypeName().contains("com.internetshop.domain")
                        && Arrays.stream(m.getGenericParameterTypes()).noneMatch(t -> t.getTypeName().contains("com.internetshop.domain"))),
                "Use case interfaces accept and return only DTO (no domain entities cross the boundary)");

        // Окремий DTO для кожної бізнес-операції, а не один OrderDto на всі випадки.
        try {
            check(OrderUseCases.class.getMethod("create", CreateOrderRequest.class).getReturnType() == CreatedOrderDto.class
                            && OrderUseCases.class.getMethod("place", UUID.class).getReturnType() == PlacedOrderDto.class
                            && OrderUseCases.class.getMethod("get", UUID.class).getReturnType() == OrderDetailsDto.class
                            && OrderUseCases.class.getMethod("addItem", UUID.class, AddOrderItemRequest.class).getReturnType() == OrderDetailsDto.class
                            && CatalogUseCases.class.getMethod("create", CreateProductRequest.class).getReturnType() == CreatedProductDto.class,
                    "Each business operation has its own request/result DTO");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("FAIL: use case signatures do not use request DTO", e);
        }

        // Мапінг домен -> DTO: прості типи замість Value Objects; лише потрібні сценарію поля.
        Order order = new Order(new CustomerId(UUID.randomUUID()));
        order.addItem(UUID.randomUUID(), "Миша", Money.of("100.50"), new Quantity(2));
        order.addItem(UUID.randomUUID(), "Кабель", Money.of("10"), new Quantity(1));
        OrderDetailsDto details = ORDER_MAPPER.toDetails(order);
        check(details.lines().size() == 2 && details.lines().get(0).unitPrice().compareTo(new BigDecimal("100.50")) == 0
                        && details.lines().get(0).quantity() == 2 && details.total().compareTo(new BigDecimal("211")) == 0,
                "Mapping: Money/Quantity become BigDecimal/int in OrderDetailsDto");
        order.place();
        PlacedOrderDto placed = ORDER_MAPPER.toPlaced(order);
        CreatedOrderDto created = ORDER_MAPPER.toCreated(order);
        check(placed.itemCount() == 3 && placed.status().equals("PLACED") && placed.total().compareTo(new BigDecimal("211")) == 0
                        && created.id().equals(order.getId()) && CreatedOrderDto.class.getRecordComponents().length == 2,
                "Mapping: PlacedOrderDto carries item count and total; CreatedOrderDto only Id and status");
        check(Arrays.stream(OrderDetailsDto.class.getRecordComponents()).noneMatch(c -> c.getName().equals("customerId") || c.getName().equals("version")),
                "Only needed data: order DTO does not expose customer id or the internal version");

        // Мапінг DTO -> домен: правила перевіряє Domain, а не DTO.
        Product product = PRODUCT_MAPPER.toNewProduct(new CreateProductRequest("  Миша ", "тиха", new BigDecimal("649.50")));
        check(product.getName().value().equals("Миша") && PRODUCT_MAPPER.toCreated(product).name().equals("Миша")
                        && PRODUCT_MAPPER.toDto(product).price().compareTo(new BigDecimal("649.50")) == 0,
                "Mapping: CreateProductRequest -> Product -> DTO (name normalized by the domain)");
        reject(() -> PRODUCT_MAPPER.toNewProduct(new CreateProductRequest(" ", "", BigDecimal.ONE)),
                "Invalid request DTO rejected by domain rules during mapping");
        reject(() -> ORDER_MAPPER.toNewOrder(new CreateOrderRequest(new UUID(0, 0))),
                "Empty customer id in request DTO rejected by domain during mapping");

        // Вебшар не може віддати доменний об'єкт назовні: серіалізатор приймає лише DTO.
        expect(IllegalArgumentException.class, () -> Json.write(order), "JSON refuses a domain entity (Order)");
        expect(IllegalArgumentException.class, () -> Json.write(Map.of("price", Money.of("1"))),
                "JSON refuses a domain value object (Money) even inside a map");
        expect(IllegalArgumentException.class, () -> Json.write(new Object()), "JSON does not serialize unknown types via toString()");
        check(Json.write(new CreatedProductDto(new UUID(0, 1), "Миша"))
                        .equals("{\"id\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Миша\"}"),
                "JSON writes a DTO record with exactly its fields");

        // Наскрізний сценарій через DTO працює як раніше.
        FileDataContext db = new FileDataContext(dir);
        CatalogService catalog = catalogService(db);
        OrderService orders = orderService(db, catalog, new InProcessEventBus(), CLOCK);
        UUID productId = catalog.create(new CreateProductRequest("Ноутбук", "", new BigDecimal("30000"))).id();
        UUID orderId = orders.create(new CreateOrderRequest(UUID.randomUUID())).id();
        OrderDetailsDto cart = orders.addItem(orderId, new AddOrderItemRequest(productId, 2));
        PlacedOrderDto result = orders.place(orderId);
        check(cart.total().compareTo(new BigDecimal("60000")) == 0 && result.itemCount() == 2
                        && orders.get(orderId).status().equals("PLACED") && orders.list().size() == 1,
                "Order scenario works end to end with request and result DTO");
    }

    // ---------------- ПР8: валідація вхідних даних (аналог FluentValidation) ----------------

    private static void validation(Path dir) {
        FileDataContext db = new FileDataContext(dir);
        CatalogService catalog = catalogService(db);
        UUID mouseId = catalog.create(new CreateProductRequest("Бездротова миша", "", new BigDecimal("649.50"))).id();
        FileUnitOfWork unitOfWork = new FileUnitOfWork(db);
        ProductRepository products = new FileProductRepository(db, unitOfWork);

        // Синтаксична валідація CreateOrderRequest (наш CreateOrderCommand).
        CreateOrderRequestValidator orderValidator = new CreateOrderRequestValidator();
        check(orderValidator.validate(new CreateOrderRequest(UUID.randomUUID())).isValid(),
                "Validation: correct CreateOrderRequest passes");
        ValidationResult noCustomer = orderValidator.validate(new CreateOrderRequest(null));
        check(!noCustomer.isValid() && noCustomer.errors().get(0).propertyName().equals("customerId")
                        && noCustomer.errors().get(0).errorMessage().contains("покупця"),
                "Validation: missing customerId -> error for field customerId with a clear message");
        check(!orderValidator.validate(new CreateOrderRequest(new UUID(0, 0))).isValid(),
                "Validation: all-zero customerId rejected before the use case");

        // Усі помилки повертаються разом; у межах одного поля — лише перша (CascadeMode.Stop).
        CreateProductRequestValidator productValidator = new CreateProductRequestValidator(products);
        ValidationResult many = productValidator.validate(new CreateProductRequest(" ", "x".repeat(1001), null));
        check(many.errors().size() == 3 && many.errors().stream().map(ValidationFailure::propertyName).toList()
                        .equals(List.of("name", "description", "price")),
                "Validation: all invalid fields reported at once (name, description, price)");
        check(many.errors().stream().filter(f -> f.propertyName().equals("name")).count() == 1,
                "Validation: one error per field (blank name is not also checked for uniqueness)");
        check(!productValidator.validate(new CreateProductRequest("Кабель", "", new BigDecimal("-5"))).isValid()
                        && !productValidator.validate(new CreateProductRequest("Кабель", "", new BigDecimal("1000000.01"))).isValid()
                        && !productValidator.validate(new CreateProductRequest("Кабель", "", new BigDecimal("1.999"))).isValid()
                        && productValidator.validate(new CreateProductRequest("Кабель", "", new BigDecimal("1000000"))).isValid(),
                "Validation: price must be positive, at most 1 000 000 and have at most 2 decimals");

        // Семантична валідація з доступом до даних: унікальна назва товару, існування товару в каталозі.
        ValidationResult duplicate = productValidator.validate(new CreateProductRequest("  бездротова МИША ", "", BigDecimal.TEN));
        check(!duplicate.isValid() && duplicate.errors().get(0).errorMessage().contains("вже є"),
                "Semantic validation (data access): product name must be unique in the catalog");
        AddOrderItemRequestValidator itemValidator = new AddOrderItemRequestValidator(new CatalogProductAdapter(catalog));
        check(itemValidator.validate(new AddOrderItemRequest(mouseId, 2)).isValid()
                        && !itemValidator.validate(new AddOrderItemRequest(UUID.randomUUID(), 1)).isValid(),
                "Semantic validation (data access): product must exist in the catalog");
        ValidationResult zero = itemValidator.validate(new AddOrderItemRequest(mouseId, 0));
        check(!zero.isValid() && zero.errors().get(0).propertyName().equals("quantity"),
                "Validation: quantity must be positive");
        check(itemValidator.validate(new AddOrderItemRequest(mouseId, 100)).isValid(),
                "Validation checks format only: the 1..99 limit stays a domain invariant of Order");

        // validateAndThrow: виняток несе всі помилки.
        try {
            productValidator.validateAndThrow(new CreateProductRequest("", "", null));
            throw new AssertionError("FAIL: invalid request accepted by validateAndThrow");
        } catch (ValidationException e) {
            check(e.errors().size() == 2, "validateAndThrow raises ValidationException with every failure");
        }

        // Реєстрація в DI: валідатори Scoped, для кожного вхідного DTO є валідатор.
        ServiceProvider services = InternetShopApp.configureServices(dir.resolve("di"));
        AutoValidation auto = services.get(AutoValidation.class);
        check(services.lifetimeOf(CreateOrderRequestValidator.class) == Lifetime.SCOPED
                        && services.lifetimeOf(AddOrderItemRequestValidator.class) == Lifetime.SCOPED
                        && services.lifetimeOf(CreateProductRequestValidator.class) == Lifetime.SCOPED,
                "DI: validators registered as Scoped (they may use request-scoped repositories)");
        check(auto.hasValidatorFor(CreateOrderRequest.class) && auto.hasValidatorFor(AddOrderItemRequest.class)
                        && auto.hasValidatorFor(CreateProductRequest.class),
                "Auto validation knows a validator for every request DTO");
        try (ServiceScope scope = services.createScope()) {
            expect(ValidationException.class, () -> auto.validate(scope, new CreateOrderRequest(null)),
                    "Auto validation resolves the validator from the request scope and rejects the model");
        }

        // Валідатори в Application, без залежностей від WebApi; DTO лишаються простими даними.
        check(Stream.of(CreateOrderRequestValidator.class, AddOrderItemRequestValidator.class, CreateProductRequestValidator.class)
                        .allMatch(c -> c.getPackageName().startsWith("com.internetshop.application")
                                && c.getSuperclass() == AbstractValidator.class),
                "Validators live in the Application layer and extend AbstractValidator<T>");
    }

    /** Читання «після перезапуску»: новий контекст даних над тими самими файлами. */
    private static ProductRepository productsAfterRestart(Path dir) {
        FileDataContext db = new FileDataContext(dir);
        return new FileProductRepository(db, new FileUnitOfWork(db));
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
        check(consumers.stream().noneMatch(i -> i.contains("OrderService") || i.contains("OrderRepository")),
                "Event handlers do not call Ordering services or stores");

        // ПР3: сценарії не звертаються до системного годинника напряму, Router не знає винятків внутрішніх шарів.
        check(sourcesOf(application).stream().noneMatch(src -> src.contains("Instant.now(") || src.contains("System.currentTimeMillis(")
                        || src.contains("LocalDateTime.now(")),
                "DIP: Application does not read the system clock directly (uses TimeProvider)");
        Path router = root.resolve("internetshop-webapi/src/main/java/com/internetshop/webapi/http/Router.java");
        check(Files.readAllLines(router).stream().map(String::trim).filter(l -> l.startsWith("import "))
                        .noneMatch(i -> i.contains("com.internetshop.domain") || i.contains("com.internetshop.application")),
                "OCP: Router does not reference Domain/Application exceptions (rules live in ErrorMapper)");

        // ПР4: контейнер не передається в бізнес-логіку, а залежності не створюються всередині класів.
        Path webapi = root.resolve("internetshop-webapi/src/main/java/com/internetshop/webapi");
        Path infrastructure = root.resolve("internetshop-infrastructure/src/main/java");
        List<String> outsideRoot = new ArrayList<>(importsOf(domain));
        outsideRoot.addAll(importsOf(application));
        outsideRoot.addAll(importsOf(infrastructure));
        outsideRoot.addAll(importsOf(webapi.resolve("endpoints")));
        check(outsideRoot.stream().noneMatch(i -> i.startsWith("com.internetshop.webapi.di")),
                "DI: the container is used only in the composition root and Router, not in business logic or endpoints");
        List<String> components = new ArrayList<>(sourcesOf(application));
        components.addAll(sourcesOf(infrastructure));
        components.addAll(sourcesOf(webapi.resolve("endpoints")));
        components.addAll(sourcesOf(webapi.resolve("http")));
        Pattern directCreation = Pattern.compile("new \\w*(Service|Store|Repository|UnitOfWork|DataContext|Adapter|Mapper|EventBus|Provider|Outbox|Statistics|Handler)\\(");
        check(components.stream().noneMatch(src -> directCreation.matcher(src).find()),
                "DI: services, repositories, adapters and mappers are not created with new inside other classes");

        // ПР5: репозиторії лише перетворюють дані й реєструють зміни; з файлами працює тільки контекст даних.
        Path persistence = infrastructure.resolve("com/internetshop/infrastructure/persistence");
        check(Files.readString(persistence.resolve("FileOrderRepository.java")).lines()
                        .noneMatch(l -> l.contains("TextDataFile") || l.contains("java.nio"))
                        && Files.readString(persistence.resolve("FileProductRepository.java")).lines()
                        .noneMatch(l -> l.contains("TextDataFile") || l.contains("java.nio")),
                "Repository: implementations do not write files themselves (only via Unit of Work and data context)");
        check(sourcesOf(application).stream().noneMatch(src -> src.contains("Mapper.toDto(") || src.contains("Mapper::toDto"))
                        && ProductMapper.class.isInterface() && OrderMapper.class.isInterface(),
                "DI: mappers are injected abstractions, not static calls");

        // ПР6: ендпоінти працюють лише з DTO, а самі DTO не залежать від доменної моделі.
        check(importsOf(webapi.resolve("endpoints")).stream().noneMatch(i -> i.startsWith("com.internetshop.domain")),
                "DTO: HTTP endpoints do not import domain classes");
        List<String> dtoImports = new ArrayList<>(importsOf(application.resolve("com/internetshop/application/ordering/dto")));
        dtoImports.addAll(importsOf(application.resolve("com/internetshop/application/catalog/dto")));
        check(dtoImports.stream().allMatch(i -> i.startsWith("java.")),
                "DTO: DTO packages depend only on the JDK (no domain classes inside)");

        // ПР8: перевірки не в контролерах — ендпоінти не валідують і не кидають винятків самі.
        check(importsOf(webapi.resolve("endpoints")).stream().noneMatch(i -> i.contains(".validation."))
                        && sourcesOf(webapi.resolve("endpoints")).stream().noneMatch(src -> src.contains("throw new")),
                "Validation: endpoints contain no validation logic (validators run automatically before them)");
    }

    // ---------------- ПР7: архітектурний стиль — модульний моноліт ----------------

    /**
     * Фітнес-функції стилю: перевіряють ті властивості, на які спирається аналіз у ПР7.
     * Якщо хтось почне «розмивати» модулі чи межу між контекстами, збірка впаде.
     */
    private static void architecturalStyle(Path root) throws IOException {
        List<String> modules = List.of("internetshop-domain", "internetshop-application",
                "internetshop-infrastructure", "internetshop-webapi");

        // Моноліт: одна одиниця розгортання (виконуваний jar webapi) з однією точкою входу.
        long entryPoints = 0;
        for (String m : modules) {
            entryPoints += sourcesOf(root.resolve(m + "/src/main/java")).stream()
                    .filter(src -> src.contains("static void main(")).count();
        }
        check(entryPoints == 1, "Monolith: one deployable unit with a single entry point (webapi Main)");

        // Модульність: Maven-модулі залежать лише «всередину», без циклів.
        Map<String, Set<String>> allowed = Map.of(
                "internetshop-domain", Set.of(),
                "internetshop-application", Set.of("internetshop-domain"),
                "internetshop-infrastructure", Set.of("internetshop-domain", "internetshop-application"),
                "internetshop-webapi", Set.of("internetshop-domain", "internetshop-application", "internetshop-infrastructure"));
        boolean inward = true;
        for (String m : modules) {
            String pom = Files.readString(root.resolve(m + "/pom.xml"));
            String deps = pom.contains("<dependencies>") ? pom.substring(pom.indexOf("<dependencies>")) : "";
            for (String internal : modules) {
                if (deps.contains("<artifactId>" + internal + "</artifactId>") && !allowed.get(m).contains(internal)) inward = false;
            }
        }
        check(inward, "Modular: Maven modules depend only inward (Domain <- Application <- Infrastructure <- WebApi)");

        // Точка майбутнього розділення: Ordering і Catalog зустрічаються лише в адаптері (і в точці складання).
        // Якщо Catalog стане окремим сервісом, адаптер заміниться HTTP-клієнтом, решта коду не зміниться.
        List<String> crossing = new ArrayList<>();
        for (String m : List.of("internetshop-application", "internetshop-infrastructure", "internetshop-webapi")) {
            try (Stream<Path> files = Files.walk(root.resolve(m + "/src/main/java"))) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    List<String> imports = Files.readAllLines(f).stream().map(String::trim).filter(l -> l.startsWith("import ")).toList();
                    if (imports.stream().anyMatch(i -> i.contains(".catalog.")) && imports.stream().anyMatch(i -> i.contains(".ordering.")))
                        crossing.add(f.getFileName().toString());
                }
            }
        }
        check(Set.copyOf(crossing).equals(Set.of("CatalogProductAdapter.java", "InternetShopApp.java")),
                "Split point: Ordering reaches Catalog only through CatalogProductAdapter (wired in the composition root)");

        // Узгодженість: подія публікується лише після збереження — у моноліті це одна локальна операція;
        // у мікросервісах тут знадобився б Outbox, бо запис і відправлення стали б двома окремими кроками.
        String orderService = Files.readString(root.resolve(
                "internetshop-application/src/main/java/com/internetshop/application/ordering/OrderService.java"));
        boolean savedFirst = true;
        for (int at = orderService.indexOf("events.publish("); at >= 0; at = orderService.indexOf("events.publish(", at + 1)) {
            int save = orderService.lastIndexOf("unitOfWork.saveChanges()", at);
            int method = orderService.lastIndexOf("public ", at);
            if (save < method) savedFirst = false;
        }
        check(orderService.contains("events.publish(") && savedFirst,
                "Consistency: OrderService publishes events only after saveChanges() in the same method");
    }

    private static List<String> sourcesOf(Path dir) throws IOException {
        List<String> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) sources.add(Files.readString(f));
        }
        return sources;
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
            check(order.body().equals("{\"id\":\"" + orderId + "\",\"status\":\"DRAFT\"}"),
                    "POST /orders returns CreatedOrderDto: only id and status");

            HttpResponse<String> tooMany = send(client, "POST", base + "/orders/" + orderId + "/items", "application/json",
                    "{\"productId\":\"" + productId + "\",\"quantity\":100}");
            check(tooMany.statusCode() == 400 && tooMany.body().contains("від 1 до 99") && !tooMany.body().contains("\"errors\""),
                    "Domain invariant violation (passes validation) -> 400 with the domain message");

            // ПР8: автоматична валідація до use-case, структурована відповідь з помилками за полями.
            HttpResponse<String> badProduct = send(client, "POST", base + "/products", "application/json",
                    "{\"name\":\" \",\"description\":\"\",\"price\":12.345}");
            check(badProduct.statusCode() == 400 && badProduct.body().contains("\"title\":\"Вхідні дані не пройшли перевірку.\"")
                            && badProduct.body().contains("\"errors\":{\"name\":[") && badProduct.body().contains("\"price\":["),
                    "Validation error -> 400 with structured errors grouped by field");
            check(!badProduct.body().contains("12.345"), "Validation response does not echo submitted values");
            check(!send(client, "GET", base + "/products", null, null).body().contains("\"name\":\" \""),
                    "Invalid request never reaches the use case (nothing saved)");
            HttpResponse<String> duplicate = send(client, "POST", base + "/products", "application/json",
                    "{\"name\":\"бездротова миша\",\"description\":\"\",\"price\":10}");
            check(duplicate.statusCode() == 400 && duplicate.body().contains("вже є"),
                    "Semantic validation over HTTP: duplicate product name -> 400");
            HttpResponse<String> noCustomer = send(client, "POST", base + "/orders", "application/json", "{}");
            check(noCustomer.statusCode() == 400 && noCustomer.body().contains("\"customerId\":["),
                    "POST /orders without customerId -> 400 from CreateOrderRequestValidator");
            HttpResponse<String> badItem = send(client, "POST", base + "/orders/" + orderId + "/items", "application/json",
                    "{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":0}");
            check(badItem.statusCode() == 400 && badItem.body().contains("немає в каталозі") && badItem.body().contains("\"quantity\":["),
                    "Unknown product and zero quantity reported together -> 400");

            HttpResponse<String> added = send(client, "POST", base + "/orders/" + orderId + "/items", "application/json",
                    "{\"productId\":\"" + productId + "\",\"quantity\":2}");
            check(added.statusCode() == 200 && added.body().contains("\"total\":21.00"), "Item added via HTTP, total 21.00");

            HttpResponse<String> placed = send(client, "POST", base + "/orders/" + orderId + "/place", "application/json", "{}");
            check(placed.statusCode() == 200 && placed.body().contains("\"PLACED\""), "Order placed via HTTP");
            check(placed.body().contains("\"itemCount\":2") && !placed.body().contains("\"lines\""),
                    "POST /orders/{id}/place returns PlacedOrderDto (item count, no lines)");
            HttpResponse<String> allOrders = send(client, "GET", base + "/orders", null, null);
            check(allOrders.statusCode() == 200 && allOrders.body().contains(orderId) && allOrders.body().contains("\"lines\"")
                            && !allOrders.body().contains("customerId") && !allOrders.body().contains("version"),
                    "GET /orders returns DTO without customer ids or internal version (no data leak)");

            HttpResponse<String> notifications = send(client, "GET", base + "/notifications", null, null);
            check(notifications.statusCode() == 200 && notifications.body().contains(orderId)
                    && notifications.body().contains("21.00") && !notifications.body().contains("customerId"),
                    "GET /notifications shows reaction to OrderPlaced without exposing customer ids");
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
