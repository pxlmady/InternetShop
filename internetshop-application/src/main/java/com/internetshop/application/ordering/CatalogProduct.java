package com.internetshop.application.ordering;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Як контекст Ordering бачить товар каталогу: лише те, що потрібно для рядка замовлення.
 * Модель Catalog (Product, ProductName, опис) сюди навмисно не потрапляє.
 */
public record CatalogProduct(UUID id, String name, BigDecimal price) {
}
