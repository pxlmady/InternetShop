package com.internetshop.application.ordering;

import com.internetshop.domain.ordering.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ПР5 (Repository): колекція замовлень з точки зору прикладного рівня (раніше — OrderStore).
 * Агрегат читається й додається цілком: замовлення + рядки. Інтерфейс належить Application,
 * реалізація — FileOrderRepository в Infrastructure; як саме зберігаються дані, сценарії не знають.
 * <p>
 * Методи репозиторію лише реєструють зміни. Записує їх разом, атомарно, UnitOfWork.saveChanges().
 */
public interface OrderRepository {

    List<Order> findAll();

    /** Повертає окрему копію агрегату; зміни не видно іншим, доки їх не збережено через UnitOfWork. */
    Optional<Order> findById(UUID id);

    /** Додає нове замовлення до колекції (буде записане під час UnitOfWork.saveChanges). */
    void add(Order order);

    /**
     * Позначає змінений агрегат для збереження.
     * Під час saveChanges перевіряється версія: якщо після читання замовлення вже змінив інший запит,
     * виникає {@link com.internetshop.application.shared.ConcurrentChangeException}.
     */
    void update(Order order);
}
