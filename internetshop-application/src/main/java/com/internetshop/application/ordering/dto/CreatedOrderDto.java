package com.internetshop.application.ordering.dto;

import java.util.UUID;

/** ПР6 (DTO): результат створення замовлення — лише Id нового замовлення та його початковий статус. */
public record CreatedOrderDto(UUID id, String status) {
}
