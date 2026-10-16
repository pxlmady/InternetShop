package com.internetshop.application.notifications;

import java.util.List;

/**
 * Контракт «переглянути надіслані сповіщення» (ПР3, ISP). Потрібен лише HTTP-маршруту
 * GET /notifications: він може читати сповіщення, але не може їх створювати.
 */
public interface NotificationFeed {

    /** Останні сповіщення, від нових до старих. */
    List<Notification> findRecent();
}
