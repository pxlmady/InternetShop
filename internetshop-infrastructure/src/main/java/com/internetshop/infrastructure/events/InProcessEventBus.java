package com.internetshop.infrastructure.events;

import com.internetshop.application.shared.events.Event;
import com.internetshop.application.shared.events.EventBus;
import com.internetshop.application.shared.events.EventHandler;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Шина подій у межах одного процесу (аналог InProcessEventBus з методички).
 * Обробники підписуються на тип події в точці складання; publish передає подію кожному з них.
 * Доставка синхронна (у тому самому потоці), без черги та брокера повідомлень.
 * Помилка одного обробника не скасовує вже збережену операцію і не заважає іншим обробникам.
 */
public final class InProcessEventBus implements EventBus {

    private static final Logger LOG = Logger.getLogger(InProcessEventBus.class.getName());

    private record Subscription(Class<? extends Event> type, EventHandler<? extends Event> handler) { }

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public <E extends Event> void subscribe(Class<E> type, EventHandler<E> handler) {
        subscriptions.add(new Subscription(Objects.requireNonNull(type), Objects.requireNonNull(handler)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void publish(Event event) {
        Objects.requireNonNull(event, "event");
        for (Subscription s : subscriptions) {
            if (!s.type().isInstance(event)) continue;
            try {
                ((EventHandler<Event>) s.handler()).handle(event);
            } catch (RuntimeException e) {
                // У лог — лише тип події та обробник, без даних події (там можуть бути дані покупця).
                LOG.log(Level.WARNING, "Event handler " + s.handler().getClass().getSimpleName()
                        + " failed for " + event.getClass().getSimpleName(), e);
            }
        }
    }
}
