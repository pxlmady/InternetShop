package com.internetshop.application.ordering.events;

import com.internetshop.application.shared.events.Event;

import java.time.Instant;
import java.util.UUID;

/** Подія: створено нове замовлення-чернетку. Містить лише мінімально потрібні дані. */
public record OrderCreated(UUID orderId, Instant occurredAt) implements Event {
}
