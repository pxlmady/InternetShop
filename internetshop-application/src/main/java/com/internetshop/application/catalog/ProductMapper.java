package com.internetshop.application.catalog;

import com.internetshop.domain.catalog.Product;

/**
 * Перетворення сутності Product на ProductDto (ПР3, SRP): окремо від сценаріїв CatalogService.
 * ПР4 (DI): абстракція замість статичного класу; CatalogService отримує її через конструктор.
 */
public interface ProductMapper {

    ProductDto toDto(Product product);
}
