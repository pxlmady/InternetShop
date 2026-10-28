package com.internetshop.application.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Сценарії контексту Catalog як абстракція (ПР4, DI): HTTP-ендпоінти залежать від цього інтерфейсу,
 * а не від конкретного CatalogService. Читання успадковане від вузького ProductQueries (ПР3, ISP).
 */
public interface CatalogUseCases extends ProductQueries {

    ProductDto create(String name, String description, BigDecimal price);

    /** @throws ProductNotFoundException якщо товару немає */
    void delete(UUID id);
}
