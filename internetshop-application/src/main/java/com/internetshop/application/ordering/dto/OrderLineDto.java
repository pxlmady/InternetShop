package com.internetshop.application.ordering.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Рядок замовлення у відповіді: «знімок» назви й ціни, кількість і сума рядка. Частина OrderDetailsDto. */
public record OrderLineDto(UUID productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
}
