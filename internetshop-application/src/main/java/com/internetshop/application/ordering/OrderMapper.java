package com.internetshop.application.ordering;

import com.internetshop.domain.ordering.Order;
import com.internetshop.domain.ordering.OrderLine;

import java.util.List;

/**
 * Перетворення агрегату Order на DTO для зовнішніх шарів (ПР3, SRP).
 * Раніше це робив сам OrderService; тепер зміна формату відповіді не зачіпає сценарії замовлень.
 */
public final class OrderMapper {

    private OrderMapper() { }

    public static OrderDto toDto(Order order) {
        List<OrderLineDto> lines = order.getLines().stream().map(OrderMapper::toDto).toList();
        return new OrderDto(order.getId(), order.getCustomerId().value(), order.getStatus().name(),
                lines, order.total().amount());
    }

    private static OrderLineDto toDto(OrderLine line) {
        return new OrderLineDto(line.getProductId(), line.getProductName(), line.getUnitPrice().amount(),
                line.getQuantity().value(), line.lineTotal().amount());
    }
}
