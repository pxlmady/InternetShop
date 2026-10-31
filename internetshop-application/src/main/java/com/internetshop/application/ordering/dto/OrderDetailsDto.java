package com.internetshop.application.ordering.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * ПР6 (DTO): вміст замовлення («кошик») для сценаріїв перегляду — список, одне замовлення
 * і результат додавання товару (клієнт одразу показує оновлений кошик).
 * Ідентифікатор покупця навмисно не передається: для показу кошика він не потрібен,
 * а в GET /orders розкривав би, яким покупцям належать чужі замовлення.
 * Версія агрегату (для оптимістичного блокування) та внутрішні Id рядків теж лишаються всередині.
 */
public record OrderDetailsDto(UUID id, String status, List<OrderLineDto> lines, BigDecimal total) {
}
