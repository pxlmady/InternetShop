package com.internetshop.application.ordering;

import com.internetshop.domain.ordering.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Контракт збереження замовлень (агрегат зберігається цілком: замовлення + рядки). */
public interface OrderStore {

    List<Order> findAll();

    /** Повертає окрему копію агрегату; зміни не видно іншим, доки не викликано update. */
    Optional<Order> findById(UUID id);

    void add(Order order);

    /**
     * Зберігає змінений агрегат.
     * @throws com.internetshop.application.shared.ConcurrentChangeException
     *         якщо після читання замовлення вже змінив інший запит
     */
    void update(Order order);
}
