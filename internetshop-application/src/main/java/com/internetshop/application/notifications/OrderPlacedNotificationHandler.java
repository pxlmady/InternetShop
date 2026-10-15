package com.internetshop.application.notifications;

import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.application.shared.events.EventHandler;

import java.util.Objects;

/** Реакція «сповістити покупця» на подію OrderPlaced. Про OrderService нічого не знає. */
public final class OrderPlacedNotificationHandler implements EventHandler<OrderPlaced> {

    private final NotificationOutbox outbox;

    public OrderPlacedNotificationHandler(NotificationOutbox outbox) {
        this.outbox = Objects.requireNonNull(outbox);
    }

    @Override
    public void handle(OrderPlaced event) {
        String shortId = event.orderId().toString().substring(0, 8);
        String message = "Замовлення " + shortId + " оформлено. Одиниць товару: " + event.itemCount()
                + ", сума: " + event.total().toPlainString() + " грн.";
        outbox.add(new Notification(event.customerId(), event.orderId(), message, event.occurredAt()));
    }
}
