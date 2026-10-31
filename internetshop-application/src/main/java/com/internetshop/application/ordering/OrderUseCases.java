package com.internetshop.application.ordering;

import com.internetshop.application.ordering.dto.AddOrderItemRequest;
import com.internetshop.application.ordering.dto.CreateOrderRequest;
import com.internetshop.application.ordering.dto.CreatedOrderDto;
import com.internetshop.application.ordering.dto.OrderDetailsDto;
import com.internetshop.application.ordering.dto.PlacedOrderDto;

import java.util.List;
import java.util.UUID;

/**
 * Сценарії контексту Ordering як абстракція (ПР4, DI): HTTP-ендпоінти залежать від цього інтерфейсу,
 * а не від конкретного OrderService. Реалізацію вибирає лише контейнер у точці складання.
 * <p>
 * ПР6 (DTO): межа прикладного шару. На вході — лише DTO запитів (або Id з адреси), на виході —
 * лише DTO результатів; жоден доменний клас (Order, Money, CustomerId…) у сигнатурах не фігурує.
 */
public interface OrderUseCases {

    List<OrderDetailsDto> list();

    /** @throws OrderNotFoundException якщо замовлення немає */
    OrderDetailsDto get(UUID id);

    CreatedOrderDto create(CreateOrderRequest request);

    OrderDetailsDto addItem(UUID orderId, AddOrderItemRequest request);

    PlacedOrderDto place(UUID orderId);
}
