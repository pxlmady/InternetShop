package com.internetshop.application.catalog;

import com.internetshop.domain.catalog.Product;

/** Перетворення сутності Product на ProductDto (ПР3, SRP): окремо від сценаріїв CatalogService. */
public final class ProductMapper {

    private ProductMapper() { }

    public static ProductDto toDto(Product p) {
        return new ProductDto(p.getId(), p.getName().value(), p.getDescription(), p.getPrice().amount());
    }
}
