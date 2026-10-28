package com.internetshop.application.ordering;

import com.internetshop.domain.ordering.Order;
import com.internetshop.domain.ordering.OrderLine;

import java.util.List;

/** Реалізація OrderMapper без стану: легковаговий компонент, тому в контейнері — Transient (ПР4). */
public final class OrderDtoMapper implements OrderMapper {

    @Override
    public OrderDto toDto(Order order) {
        List<OrderLineDto> lines = order.getLines().stream().map(this::toDto).toList();
        return new OrderDto(order.getId(), order.getCustomerId().value(), order.getStatus().name(),
                lines, order.total().amount());
    }

    private OrderLineDto toDto(OrderLine line) {
        return new OrderLineDto(line.getProductId(), line.getProductName(), line.getUnitPrice().amount(),
                line.getQuantity().value(), line.lineTotal().amount());
    }
}
