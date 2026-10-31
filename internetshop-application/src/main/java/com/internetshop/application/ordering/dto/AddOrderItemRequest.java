package com.internetshop.application.ordering.dto;

import java.util.UUID;

/**
 * ПР6 (DTO): вхідні дані сценарію «додати товар до замовлення». Ідентифікатор замовлення береться
 * з адреси запиту, тому сюди не входить. Назву й ціну клієнт не передає: їх бере Ordering з каталогу.
 */
public record AddOrderItemRequest(UUID productId, int quantity) {
}
