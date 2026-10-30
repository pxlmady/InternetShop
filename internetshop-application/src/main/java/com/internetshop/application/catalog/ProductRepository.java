package com.internetshop.application.catalog;

import com.internetshop.domain.catalog.Product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ПР5 (Repository): колекція товарів каталогу (раніше — ProductStore). Належить Application,
 * щоб сценарії не залежали від конкретного сховища; реалізація — FileProductRepository в Infrastructure.
 * Зміни записуються лише під час UnitOfWork.saveChanges().
 */
public interface ProductRepository {

    List<Product> findAll();

    Optional<Product> findById(UUID id);

    void add(Product product);

    /** Видаляє товар; викликається лише для товару, наявність якого вже перевірено. */
    void remove(UUID id);
}
