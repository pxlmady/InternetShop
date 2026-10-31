package com.internetshop.application.ordering;

import com.internetshop.application.ordering.dto.CreateOrderRequest;
import com.internetshop.application.ordering.dto.CreatedOrderDto;
import com.internetshop.application.ordering.dto.OrderDetailsDto;
import com.internetshop.application.ordering.dto.PlacedOrderDto;
import com.internetshop.domain.ordering.Order;

/**
 * Перетворення між агрегатом Order і DTO для зовнішніх шарів (ПР3, SRP).
 * ПР4 (DI): раніше це був статичний клас, і OrderService викликав його напряму (статична залежність).
 * Тепер це абстракція (аналог IOrderMapper з методички), яку OrderService отримує через конструктор,
 * а реалізація OrderDtoMapper реєструється в контейнері як Transient.
 * <p>
 * ПР6 (DTO + мапінг): мапінг в обидва боки і окремий DTO на кожну бізнес-операцію (замість одного
 * OrderDto для всього). Мапер — єдине місце, де DTO «зустрічаються» з доменною моделлю.
 */
public interface OrderMapper {

    /** Вхідний DTO → новий агрегат. Правила (непорожній покупець) перевіряє Domain. */
    Order toNewOrder(CreateOrderRequest request);

    /** Перегляд замовлення: список, одне замовлення, результат додавання товару. */
    OrderDetailsDto toDetails(Order order);

    /** Результат створення замовлення. */
    CreatedOrderDto toCreated(Order order);

    /** Результат оформлення замовлення. */
    PlacedOrderDto toPlaced(Order order);
}
