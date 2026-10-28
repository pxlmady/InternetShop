package com.internetshop.application.catalog;

import com.internetshop.domain.catalog.Product;

/** Реалізація ProductMapper без стану: у контейнері — Transient (ПР4). */
public final class ProductDtoMapper implements ProductMapper {

    @Override
    public ProductDto toDto(Product p) {
        return new ProductDto(p.getId(), p.getName().value(), p.getDescription(), p.getPrice().amount());
    }
}
