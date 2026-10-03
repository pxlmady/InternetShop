package com.internetshop.application.catalog;

import com.internetshop.domain.catalog.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Контракт збереження товарів. Належить Application, щоб сценарії не залежали
 * від конкретного сховища; реалізація — FileProductStore в Infrastructure.
 */
public interface ProductStore {

    List<Product> findAll();

    Optional<Product> findById(UUID id);

    void add(Product product);

    /** Видаляє товар; викликається лише для товару, наявність якого вже перевірено. */
    void remove(UUID id);
}
