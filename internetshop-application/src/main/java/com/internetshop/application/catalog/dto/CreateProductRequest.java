package com.internetshop.application.catalog.dto;

import java.math.BigDecimal;

/**
 * ПР6 (DTO): вхідні дані сценарію «додати товар до каталогу». Id не передається — його створює Domain.
 * Перевірки (назва, ціна) виконують ProductName, Money і Product під час мапінгу в доменну модель.
 */
public record CreateProductRequest(String name, String description, BigDecimal price) {
}
