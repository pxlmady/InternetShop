package com.internetshop.application.catalog.dto;

import java.util.UUID;

/** ПР6 (DTO): результат додавання товару — Id і нормалізована назва (без зайвих пробілів). */
public record CreatedProductDto(UUID id, String name) {
}
