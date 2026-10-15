package com.internetshop.application.shared.events;

/**
 * Контракт публікації подій. Ініціатор (producer) знає лише цей
 * інтерфейс: він повідомляє «що сталося», але не знає, хто і як на це відреагує.
 * Реалізація — InProcessEventBus в Infrastructure; підписки налаштовуються в точці складання.
 */
public interface EventBus {

    void publish(Event event);
}
