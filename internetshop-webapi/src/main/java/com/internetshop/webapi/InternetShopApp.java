package com.internetshop.webapi;

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
import com.internetshop.application.notifications.NotificationFeed;
import com.internetshop.application.notifications.NotificationSender;
import com.internetshop.application.notifications.OrderPlacedNotificationHandler;
import com.internetshop.application.ordering.OrderDtoMapper;
import com.internetshop.application.ordering.OrderMapper;
import com.internetshop.application.ordering.OrderService;
import com.internetshop.application.ordering.OrderRepository;
import com.internetshop.application.ordering.OrderUseCases;
import com.internetshop.application.ordering.ProductCatalog;
import com.internetshop.application.ordering.events.OrderCreated;
import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.application.shared.ConcurrentChangeException;
import com.internetshop.application.shared.NotFoundException;
import com.internetshop.application.shared.TimeProvider;
import com.internetshop.application.shared.UnitOfWork;
import com.internetshop.application.shared.events.EventBus;
import com.internetshop.domain.shared.DomainRuleException;
import com.internetshop.infrastructure.events.InProcessEventBus;
import com.internetshop.infrastructure.integration.CatalogProductAdapter;
import com.internetshop.infrastructure.notifications.InMemoryNotificationOutbox;
import com.internetshop.infrastructure.persistence.FileDataContext;
import com.internetshop.infrastructure.persistence.FileOrderRepository;
import com.internetshop.infrastructure.persistence.FileProductRepository;
import com.internetshop.infrastructure.persistence.FileUnitOfWork;
import com.internetshop.infrastructure.time.SystemTimeProvider;
import com.internetshop.webapi.di.ServiceCollection;
import com.internetshop.webapi.di.ServiceProvider;
import com.internetshop.webapi.di.ServiceScope;
import com.internetshop.webapi.di.ServiceScopeFactory;
import com.internetshop.webapi.endpoints.NotificationEndpoints;
import com.internetshop.webapi.endpoints.OrderEndpoints;
import com.internetshop.webapi.endpoints.ProductEndpoints;
import com.internetshop.webapi.endpoints.StatisticsEndpoints;
import com.internetshop.webapi.http.ErrorMapper;
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
 * <p>
 * ПР4 (Dependency Injection, IoC): об'єкти більше не створюються вручну через new у потрібному порядку.
 * Метод configureServices лише реєструє в контейнері пари «абстракція → реалізація» з життєвим циклом
 * (аналог builder.Services.AddSingleton/AddScoped/AddTransient у Program.cs), а створює об'єкти
 * і передає їм залежності через конструктори сам контейнер. Контейнер використовується тільки тут і в Router.
 * <p>
 * ПР5 (Repository + Unit of Work): тут вибираються реалізації OrderRepository, ProductRepository і UnitOfWork,
 * як AddScoped&lt;IOrderRepository, ...&gt;() / AddScoped&lt;IUnitOfWork, UnitOfWork&gt;() у методичці.
 */
public final class InternetShopApp {

    private InternetShopApp() { }

    /** Реєстрація залежностей і їхніх життєвих циклів. */
    public static ServiceProvider configureServices(Path dataDirectory) {
        ServiceCollection services = new ServiceCollection();

        // ---- Singleton: один екземпляр на весь застосунок ----
        // Контекст даних (аналог AppDbContext) тримає єдину копію збережених даних і синхронізує запис у файли.
        // Два екземпляри над тими самими файлами розійшлися б у даних, тому він Singleton.
        services.addSingleton(FileDataContext.class, s -> new FileDataContext(dataDirectory));
        services.addSingleton(TimeProvider.class, SystemTimeProvider.class);
        // Шина подій — як у методичці: AddSingleton<IEventBus, InProcessEventBus>().
        // Один і той самий екземпляр видно як EventBus (для publish) і як InProcessEventBus (для subscribe).
        services.addSingleton(InProcessEventBus.class, InProcessEventBus.class);
        services.addSingleton(EventBus.class, s -> s.get(InProcessEventBus.class));
        // Спільний стан для всіх запитів: сповіщення і статистика.
        services.addSingleton(InMemoryNotificationOutbox.class, InMemoryNotificationOutbox.class);
        services.addSingleton(NotificationSender.class, s -> s.get(InMemoryNotificationOutbox.class));
        services.addSingleton(NotificationFeed.class, s -> s.get(InMemoryNotificationOutbox.class));
        services.addSingleton(SalesStatistics.class, SalesStatistics.class);
        services.addSingleton(SalesReport.class, s -> s.get(SalesStatistics.class));
        // Обробники подій підписані на Singleton-шину, тож і самі Singleton (і залежать лише від Singleton).
        services.addSingleton(OrderCreatedStatisticsHandler.class, OrderCreatedStatisticsHandler.class);
        services.addSingleton(OrderPlacedStatisticsHandler.class, OrderPlacedStatisticsHandler.class);
        services.addSingleton(OrderPlacedNotificationHandler.class, OrderPlacedNotificationHandler.class);

        // ---- Scoped: один екземпляр на HTTP-запит ----
        // ПР5: Unit of Work і репозиторії — Scoped, як у методичці: в межах одного запиту всі репозиторії
        // реєструють зміни в одному Unit of Work, а сценарій зберігає їх одним saveChanges().
        // Один екземпляр FileUnitOfWork видно як UnitOfWork (для сценаріїв) і як клас (для репозиторіїв).
        services.addScoped(FileUnitOfWork.class, FileUnitOfWork.class);
        services.addScoped(UnitOfWork.class, s -> s.get(FileUnitOfWork.class));
        services.addScoped(ProductRepository.class, FileProductRepository.class);
        services.addScoped(OrderRepository.class, FileOrderRepository.class);
        // Сценарії Application, адаптер між контекстами та ендпоінти: у межах одного запиту
        // всі, кому потрібен ProductQueries / CatalogUseCases, отримують той самий CatalogService.
        services.addScoped(CatalogUseCases.class, CatalogService.class);
        services.addScoped(ProductQueries.class, s -> s.get(CatalogUseCases.class));
        services.addScoped(ProductCatalog.class, CatalogProductAdapter.class); // Ordering → Catalog (лише читання)
        services.addScoped(OrderUseCases.class, OrderService.class);           // producer подій
        services.addScoped(ProductEndpoints.class, ProductEndpoints.class);
        services.addScoped(OrderEndpoints.class, OrderEndpoints.class);
        services.addScoped(NotificationEndpoints.class, NotificationEndpoints.class);
        services.addScoped(StatisticsEndpoints.class, StatisticsEndpoints.class);

        // ---- Transient: новий екземпляр при кожному зверненні ----
        // Мапери без стану — як у методичці: AddTransient<IOrderMapper, OrderMapper>().
        services.addTransient(OrderMapper.class, OrderDtoMapper.class);
        services.addTransient(ProductMapper.class, ProductDtoMapper.class);

        // ---- HTTP ----
        // OCP: правила «виняток → HTTP-статус» задаються тут; новий тип помилки не вимагає змін у Router.
        // Правило для базового типу діє і для підтипів (ProductNotFoundException, OrderNotFoundException).
        services.addSingleton(ErrorMapper.class, s -> new ErrorMapper()
                .map(DomainRuleException.class, 400)
                .map(NotFoundException.class, 404)
                .map(ConcurrentChangeException.class, 409));
        services.addSingleton(Router.class, s -> {
            Router router = new Router(s.get(ErrorMapper.class), s.get(ServiceScopeFactory.class))
                    .staticFile("/", "index.html")
                    .staticFile("/app.js", "app.js")
                    .staticFile("/styles.css", "styles.css");
            ProductEndpoints.map(router);
            OrderEndpoints.map(router);
            NotificationEndpoints.map(router);
            StatisticsEndpoints.map(router);
            return router;
        });

        return services.build();
    }

    public static HttpServer start(int port, Path dataDirectory) throws IOException {
        ServiceProvider services = configureServices(dataDirectory);

        // Event-Driven: хто на яку подію реагує, вирішується лише тут. OrderService про це не знає;
        // нова реакція = новий обробник + реєстрація + один рядок підписки, без змін у сценаріях замовлень.
        InProcessEventBus eventBus = services.get(InProcessEventBus.class);
        eventBus.subscribe(OrderCreated.class, services.get(OrderCreatedStatisticsHandler.class));
        eventBus.subscribe(OrderPlaced.class, services.get(OrderPlacedNotificationHandler.class));
        eventBus.subscribe(OrderPlaced.class, services.get(OrderPlacedStatisticsHandler.class));

        // Початкове заповнення — окремий сценарій виконання, тому для нього створюється власна область.
        try (ServiceScope scope = services.createScope()) {
            seedCatalog(scope.get(CatalogUseCases.class));
        }

        // Слухаємо лише loopback (localhost): застосунок не доступний з інших комп'ютерів мережі.
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/", services.get(Router.class));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return server;
    }

    /** Початкові дані для демонстрації — створюються через звичайний сценарій, тож правила діють. */
    private static void seedCatalog(CatalogUseCases catalog) {
        if (!catalog.list().isEmpty()) return;
        catalog.create("Ноутбук 14\"", "Легкий ноутбук для навчання", new BigDecimal("32999.00"));
        catalog.create("Бездротова миша", "2.4 ГГц, тихі кнопки", new BigDecimal("649.50"));
        catalog.create("USB-C кабель", "1 м, 60 Вт", new BigDecimal("199"));
    }
}
