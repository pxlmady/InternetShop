package com.internetshop.application.ordering;

import com.internetshop.application.ordering.events.OrderCreated;
import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.application.shared.TimeProvider;
import com.internetshop.application.shared.events.EventBus;
import com.internetshop.domain.ordering.CustomerId;
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
 */
public final class OrderService {

    private final OrderStore store;
    private final ProductCatalog catalog;
    private final EventBus events;
    private final TimeProvider time;

    public OrderService(OrderStore store, ProductCatalog catalog, EventBus events, TimeProvider time) {
        this.store = Objects.requireNonNull(store);
        this.catalog = Objects.requireNonNull(catalog);
        this.events = Objects.requireNonNull(events);
        this.time = Objects.requireNonNull(time);
    }

    public List<OrderDto> list() {
        return store.findAll().stream().map(OrderMapper::toDto).toList();
    }

    public OrderDto get(UUID id) {
        return OrderMapper.toDto(load(id));
    }

    public OrderDto create(UUID customerId) {
        Order order = new Order(new CustomerId(customerId));
        store.add(order);
        // Подія публікується лише після збереження: вона фіксує факт, який уже відбувся.
        events.publish(new OrderCreated(order.getId(), time.now()));
        return OrderMapper.toDto(order);
    }

    public OrderDto addItem(UUID orderId, UUID productId, int quantity) {
        Order order = load(orderId);
        // Дані про товар отримуємо з іншого контексту лише через контракт ProductCatalog.
        CatalogProduct product = (productId == null ? null : catalog.findProduct(productId).orElse(null));
        if (product == null) throw new ProductNotInCatalogException();
        order.addItem(product.id(), product.name(), new Money(product.price()), new Quantity(quantity));
        store.update(order);
        return OrderMapper.toDto(order);
    }

    public OrderDto place(UUID orderId) {
        Order order = load(orderId);
        order.place();
        store.update(order);
        int itemCount = order.getLines().stream().mapToInt(l -> l.getQuantity().value()).sum();
        events.publish(new OrderPlaced(order.getId(), order.getCustomerId().value(), itemCount,
                order.total().amount(), time.now()));
        return OrderMapper.toDto(order);
    }

    private Order load(UUID id) {
        if (id == null) throw new OrderNotFoundException();
        return store.findById(id).orElseThrow(OrderNotFoundException::new);
    }
}
