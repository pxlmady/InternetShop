package com.internetshop.application.ordering;

import com.internetshop.application.ordering.dto.AddOrderItemRequest;
import com.internetshop.application.ordering.dto.CreateOrderRequest;
import com.internetshop.application.ordering.dto.CreatedOrderDto;
import com.internetshop.application.ordering.dto.OrderDetailsDto;
import com.internetshop.application.ordering.dto.PlacedOrderDto;
import com.internetshop.application.ordering.events.OrderCreated;
import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.application.shared.TimeProvider;
import com.internetshop.application.shared.UnitOfWork;
import com.internetshop.application.shared.events.EventBus;
import com.internetshop.domain.ordering.Order;
import com.internetshop.domain.ordering.Quantity;
import com.internetshop.domain.shared.Money;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Сценарії контексту Ordering: завантажити агрегат → виконати доменну дію → зберегти → повернути DTO.
 * Сервіс організовує кроки, але бізнес-рішення приймає Order.
 * Після успішного збереження сервіс публікує подію (OrderCreated, OrderPlaced) і не знає,
 * хто на неї реагує: сповіщення та статистика підписані на події окремо, в точці складання.
 * <p>
 * ПР3 (SOLID): єдина відповідальність — сценарії замовлень (SRP); перетворення на DTO винесено
 * в OrderMapper. Усі залежності — абстракції, передані через конструктор (DIP): сховище, каталог,
 * шина подій і джерело часу. Конкретні реалізації сервіс не створює і не знає.
 * <p>
 * ПР4 (DI): реалізує абстракцію OrderUseCases; мапер теж отримується через конструктор (раніше —
 * статичний виклик OrderMapper.toDto). Усі об'єкти створює контейнер; сервіс — Scoped.
 * <p>
 * ПР5 (Repository + Unit of Work): сервіс працює лише з абстракціями OrderRepository і UnitOfWork.
 * Кожен сценарій: репозиторій → доменна дія → repository.add/update → unitOfWork.saveChanges() → подія.
 * Подія публікується лише після успішного saveChanges, тобто тоді, коли зміни вже збережено.
 * <p>
 * ПР6 (DTO + мапінг): сервіс приймає DTO запитів і повертає DTO результатів. Агрегат Order живе лише
 * всередині методу: мапер створює його з CreateOrderRequest і перетворює на DTO, потрібний саме цьому
 * сценарію (CreatedOrderDto, OrderDetailsDto, PlacedOrderDto). Назовні доменні об'єкти не виходять.
 */
public final class OrderService implements OrderUseCases {

    private final OrderRepository orders;
    private final UnitOfWork unitOfWork;
    private final ProductCatalog catalog;
    private final EventBus events;
    private final TimeProvider time;
    private final OrderMapper mapper;

    public OrderService(OrderRepository orders, UnitOfWork unitOfWork, ProductCatalog catalog, EventBus events,
                        TimeProvider time, OrderMapper mapper) {
        this.orders = Objects.requireNonNull(orders);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.catalog = Objects.requireNonNull(catalog);
        this.events = Objects.requireNonNull(events);
        this.time = Objects.requireNonNull(time);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<OrderDetailsDto> list() {
        return orders.findAll().stream().map(mapper::toDetails).toList();
    }

    @Override
    public OrderDetailsDto get(UUID id) {
        return mapper.toDetails(load(id));
    }

    @Override
    public CreatedOrderDto create(CreateOrderRequest request) {
        Order order = mapper.toNewOrder(request); // DTO → домен; правила перевіряє Order / CustomerId
        orders.add(order);
        unitOfWork.saveChanges();
        // Подія публікується лише після збереження: вона фіксує факт, який уже відбувся.
        events.publish(new OrderCreated(order.getId(), time.now()));
        return mapper.toCreated(order);
    }

    @Override
    public OrderDetailsDto addItem(UUID orderId, AddOrderItemRequest request) {
        Objects.requireNonNull(request);
        Order order = load(orderId);
        // Дані про товар отримуємо з іншого контексту лише через контракт ProductCatalog.
        UUID productId = request.productId();
        CatalogProduct product = (productId == null ? null : catalog.findProduct(productId).orElse(null));
        if (product == null) throw new ProductNotInCatalogException();
        order.addItem(product.id(), product.name(), new Money(product.price()), new Quantity(request.quantity()));
        orders.update(order);
        unitOfWork.saveChanges();
        return mapper.toDetails(order);
    }

    @Override
    public PlacedOrderDto place(UUID orderId) {
        Order order = load(orderId);
        order.place();
        orders.update(order);
        unitOfWork.saveChanges();
        events.publish(new OrderPlaced(order.getId(), order.getCustomerId().value(), order.itemCount(),
                order.total().amount(), time.now()));
        return mapper.toPlaced(order);
    }

    private Order load(UUID id) {
        if (id == null) throw new OrderNotFoundException();
        return orders.findById(id).orElseThrow(OrderNotFoundException::new);
    }
}
