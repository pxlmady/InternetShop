package com.internetshop.application.notifications;

/**
 * Контракт «надіслати сповіщення» (ПР3, ISP). Потрібен лише обробнику події OrderPlaced.
 * Раніше був частиною загального NotificationOutbox разом із читанням, тож обробник залежав
 * від методу, яким не користується. Реалізація — InMemoryNotificationOutbox в Infrastructure;
 * у реальній системі на її місці був би email/SMS-сервіс.
 */
public interface NotificationSender {

    void send(Notification notification);
}
