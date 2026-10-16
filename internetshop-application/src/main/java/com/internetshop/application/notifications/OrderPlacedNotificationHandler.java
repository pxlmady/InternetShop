package com.internetshop.application.notifications;

import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.application.shared.events.EventHandler;

import java.util.Objects;

/**
 * Реакція «сповістити покупця» на подію OrderPlaced. Про OrderService нічого не знає.
 * Залежить лише від NotificationSender: читати сповіщення обробнику не потрібно (ПР3, ISP).
 */
public final class OrderPlacedNotificationHandler implements EventHandler<OrderPlaced> {

    private final NotificationSender sender;

    public OrderPlacedNotificationHandler(NotificationSender sender) {
        this.sender = Objects.requireNonNull(sender);
    }

    @Override
    public void handle(OrderPlaced event) {
        String shortId = event.orderId().toString().substring(0, 8);
        String message = "Замовлення " + shortId + " оформлено. Одиниць товару: " + event.itemCount()
                + ", сума: " + event.total().toPlainString() + " грн.";
        sender.send(new Notification(event.customerId(), event.orderId(), message, event.occurredAt()));
    }
}
