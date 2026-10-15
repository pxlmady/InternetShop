package com.internetshop.application.shared.events;

/**
 * Обробник (consumer) подій одного типу (аналог {@code IEventHandler<T>} з методички).
 * Кожен обробник відповідає за одну окрему реакцію системи.
 */
public interface EventHandler<E extends Event> {

    void handle(E event);
}
