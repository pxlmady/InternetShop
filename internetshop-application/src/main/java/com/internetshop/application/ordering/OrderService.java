package com.internetshop.application.ordering;

import com.internetshop.application.ordering.events.OrderCreated;
import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.application.shared.events.EventBus;
import com.internetshop.domain.ordering.CustomerId;
import com.internetshop.domain.ordering.Order;
import com.internetshop.domain.ordering.OrderLine;
import com.internetshop.domain.ordering.Quantity;
import com.internetshop.domain.shared.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Сценарії контексту Ordering: завантажити агрегат → виконати доменну дію → зберегти → повернути DTO.
 * Сервіс організовує кроки, але бізнес-рішення приймає Order.
 * Після успішного збереження сервіс публікує подію (OrderCreated, OrderPlaced) і не знає,
 * хто на неї реагує: сповіщення та статистика підписані на події окремо, в точці складання.
 */
public final class OrderService {

    private final OrderStore store;
    private final ProductCatalog catalog;
    private final EventBus events;

    public OrderService(OrderStore store, ProductCatalog catalog, EventBus events) {
        this.store = Objects.requireNonNull(store);
        this.catalog = Objects.requireNonNull(catalog);
        this.events = Objects.requireNonNull(events);
    }

    public List<OrderDto> list() {
        return store.findAll().stream().map(OrderService::toDto).toList();
    }

    public OrderDto get(UUID id) {
        return toDto(load(id));
    }

    public OrderDto create(UUID customerId) {
        Order order = new Order(new CustomerId(customerId));
        store.add(order);
        // Подія публікується лише після збереження: вона фіксує факт, який уже відбувся.
        events.publish(new OrderCreated(order.getId(), Instant.now()));
        return toDto(order);
    }

    public OrderDto addItem(UUID orderId, UUID productId, int quantity) {
        Order order = load(orderId);
        // Дані про товар отримуємо з іншого контексту лише через контракт ProductCatalog.
        CatalogProduct product = (productId == null ? null : catalog.findProduct(productId).orElse(null));
        if (product == null) throw new ProductNotInCatalogException();
        order.addItem(product.id(), product.name(), new Money(product.price()), new Quantity(quantity));
        store.update(order);
        return toDto(order);
    }

    public OrderDto place(UUID orderId) {
        Order order = load(orderId);
        order.place();
        store.update(order);
        int itemCount = order.getLines().stream().mapToInt(l -> l.getQuantity().value()).sum();
        events.publish(new OrderPlaced(order.getId(), order.getCustomerId().value(), itemCount,
                order.total().amount(), Instant.now()));
        return toDto(order);
    }

    private Order load(UUID id) {
        if (id == null) throw new OrderNotFoundException();
        return store.findById(id).orElseThrow(OrderNotFoundException::new);
    }

    private static OrderDto toDto(Order order) {
        List<OrderLineDto> lines = order.getLines().stream().map(OrderService::toDto).toList();
        return new OrderDto(order.getId(), order.getCustomerId().value(), order.getStatus().name(),
                lines, order.total().amount());
    }

    private static OrderLineDto toDto(OrderLine line) {
        return new OrderLineDto(line.getProductId(), line.getProductName(), line.getUnitPrice().amount(),
                line.getQuantity().value(), line.lineTotal().amount());
    }
}
