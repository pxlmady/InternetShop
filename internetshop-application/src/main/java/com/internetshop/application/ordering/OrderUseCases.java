package com.internetshop.application.ordering;

import java.util.List;
import java.util.UUID;

/**
 * Сценарії контексту Ordering як абстракція (ПР4, DI): HTTP-ендпоінти залежать від цього інтерфейсу,
 * а не від конкретного OrderService. Реалізацію вибирає лише контейнер у точці складання.
 */
public interface OrderUseCases {

    List<OrderDto> list();

    /** @throws OrderNotFoundException якщо замовлення немає */
    OrderDto get(UUID id);

    OrderDto create(UUID customerId);

    OrderDto addItem(UUID orderId, UUID productId, int quantity);

    OrderDto place(UUID orderId);
}
