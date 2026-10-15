package com.internetshop.infrastructure.notifications;

import com.internetshop.application.notifications.Notification;
import com.internetshop.application.notifications.NotificationOutbox;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Імітація надсилання сповіщень: зберігає останні сповіщення в пам'яті (з нуля після перезапуску).
 * Кількість обмежена, щоб пам'ять не росла безкінечно.
 */
public final class InMemoryNotificationOutbox implements NotificationOutbox {

    static final int MAX_KEPT = 50;

    private final Deque<Notification> recent = new ArrayDeque<>();

    @Override
    public synchronized void add(Notification notification) {
        recent.addFirst(Objects.requireNonNull(notification));
        if (recent.size() > MAX_KEPT) recent.removeLast();
    }

    @Override
    public synchronized List<Notification> findRecent() {
        return List.copyOf(recent);
    }
}
