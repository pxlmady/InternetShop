package com.internetshop.application.ordering.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** ПР6 (DTO): підтвердження оформлення — статус, кількість одиниць і сума, без переліку рядків. */
public record PlacedOrderDto(UUID id, String status, int itemCount, BigDecimal total) {
}
