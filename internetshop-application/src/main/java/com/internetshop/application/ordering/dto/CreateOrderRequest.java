package com.internetshop.application.ordering.dto;

import java.util.UUID;

/**
 * ПР6 (DTO): вхідні дані сценарію «створити замовлення» (аналог CreateOrderCommand з методички).
 * Лише дані, без перевірок і поведінки: правила (непорожній покупець) перевіряє Domain під час мапінгу.
 */
public record CreateOrderRequest(UUID customerId) {
}
