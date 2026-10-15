package com.internetshop.application.shared.events;

import java.time.Instant;

/**
 * Подія — факт, який уже відбувся в системі (назва в минулому часі: OrderCreated, OrderPlaced).
 * Подія лише переносить дані про факт: бізнес-логіки в ній немає, і вона не знає, хто на неї реагуватиме.
 */
public interface Event {

    /** Коли відбувся факт. */
    Instant occurredAt();
}
