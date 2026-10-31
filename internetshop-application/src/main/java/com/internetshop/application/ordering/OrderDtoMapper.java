package com.internetshop.application.ordering;

import com.internetshop.application.ordering.dto.CreateOrderRequest;
import com.internetshop.application.ordering.dto.CreatedOrderDto;
import com.internetshop.application.ordering.dto.OrderDetailsDto;
import com.internetshop.application.ordering.dto.OrderLineDto;
import com.internetshop.application.ordering.dto.PlacedOrderDto;
import com.internetshop.domain.ordering.CustomerId;
import com.internetshop.domain.ordering.Order;
import com.internetshop.domain.ordering.OrderLine;

import java.util.List;
import java.util.Objects;

/**
 * Ручний мапінг Order ↔ DTO: явне перетворення без сторонніх бібліотек (ПР6).
 * Value Objects (Money, Quantity, CustomerId) і перелік OrderStatus перетворюються на прості типи
 * (BigDecimal, int, UUID, String), тож за межі Application доменні класи не виходять.
 * Без стану: легковаговий компонент, тому в контейнері — Transient (ПР4).
 */
public final class OrderDtoMapper implements OrderMapper {

    @Override
    public Order toNewOrder(CreateOrderRequest request) {
        Objects.requireNonNull(request);
        return new Order(new CustomerId(request.customerId()));
    }

    @Override
    public OrderDetailsDto toDetails(Order order) {
        List<OrderLineDto> lines = order.getLines().stream().map(this::toLine).toList();
        return new OrderDetailsDto(order.getId(), order.getStatus().name(), lines, order.total().amount());
    }

    @Override
    public CreatedOrderDto toCreated(Order order) {
        return new CreatedOrderDto(order.getId(), order.getStatus().name());
    }

    @Override
    public PlacedOrderDto toPlaced(Order order) {
        return new PlacedOrderDto(order.getId(), order.getStatus().name(), order.itemCount(), order.total().amount());
    }

    private OrderLineDto toLine(OrderLine line) {
        return new OrderLineDto(line.getProductId(), line.getProductName(), line.getUnitPrice().amount(),
                line.getQuantity().value(), line.lineTotal().amount());
    }
}
