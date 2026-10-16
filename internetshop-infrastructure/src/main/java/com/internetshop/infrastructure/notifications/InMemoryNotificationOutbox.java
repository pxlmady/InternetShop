package com.internetshop.infrastructure.notifications;

import com.internetshop.application.notifications.Notification;
import com.internetshop.application.notifications.NotificationFeed;
import com.internetshop.application.notifications.NotificationSender;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Імітація надсилання сповіщень: зберігає останні сповіщення в пам'яті (з нуля після перезапуску).
 * Кількість обмежена, щоб пам'ять не росла безкінечно.
 * Один клас реалізує два вузькі контракти (ПР3, ISP): обробник події бачить його лише як
 * NotificationSender, а HTTP-маршрут — лише як NotificationFeed.
 */
public final class InMemoryNotificationOutbox implements NotificationSender, NotificationFeed {

    static final int MAX_KEPT = 50;

    private final Deque<Notification> recent = new ArrayDeque<>();

    @Override
    public synchronized void send(Notification notification) {
        recent.addFirst(Objects.requireNonNull(notification));
        if (recent.size() > MAX_KEPT) recent.removeLast();
    }

    @Override
    public synchronized List<Notification> findRecent() {
        return List.copyOf(recent);
    }
}
