package com.internetshop.application.ordering;

import com.internetshop.domain.ordering.Order;

/**
 * Перетворення агрегату Order на DTO для зовнішніх шарів (ПР3, SRP).
 * ПР4 (DI): раніше це був статичний клас, і OrderService викликав його напряму (статична залежність).
 * Тепер це абстракція (аналог IOrderMapper з методички), яку OrderService отримує через конструктор,
 * а реалізація OrderDtoMapper реєструється в контейнері як Transient.
 */
public interface OrderMapper {

    OrderDto toDto(Order order);
}
