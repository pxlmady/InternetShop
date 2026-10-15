package com.internetshop.application.ordering.events;

import com.internetshop.application.shared.events.Event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Подія: замовлення оформлено (DRAFT → PLACED). Передає лише те, що потрібно обробникам:
 * кому надіслати сповіщення (customerId) і підсумки для статистики (кількість одиниць, сума).
 * Рядки замовлення, назви товарів та доменні об'єкти сюди навмисно не потрапляють.
 */
public record OrderPlaced(UUID orderId, UUID customerId, int itemCount, BigDecimal total, Instant occurredAt)
        implements Event {
}