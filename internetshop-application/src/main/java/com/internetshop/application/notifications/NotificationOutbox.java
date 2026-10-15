package com.internetshop.application.notifications;

import java.util.List;

/**
 * Контракт «скриньки вихідних сповіщень». Реалізація — InMemoryNotificationOutbox в Infrastructure;
 * у реальній системі на її місці був би email/SMS-сервіс.
 */
public interface NotificationOutbox {

    void add(Notification notification);

    /** Останні сповіщення, від нових до старих. */
    List<Notification> findRecent();
}
