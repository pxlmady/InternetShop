package com.internetshop.infrastructure.integration;

import com.internetshop.application.catalog.CatalogService;
import com.internetshop.application.catalog.ProductDto;
import com.internetshop.application.catalog.ProductNotFoundException;
import com.internetshop.application.ordering.CatalogProduct;
import com.internetshop.application.ordering.ProductCatalog;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Адаптер між bounded contexts (антикорупційний шар): реалізує контракт Ordering (ProductCatalog)
 * через публічний сервіс Catalog і перекладає ProductDto у CatalogProduct.
 * Якщо Catalog колись стане окремим сервісом, зміниться лише цей клас.
 */
public final class CatalogProductAdapter implements ProductCatalog {

    private final CatalogService catalog;

    public CatalogProductAdapter(CatalogService catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    @Override
    public Optional<CatalogProduct> findProduct(UUID productId) {
        try {
            ProductDto p = catalog.get(productId);
            return Optional.of(new CatalogProduct(p.id(), p.name(), p.price()));
        } catch (ProductNotFoundException e) {
            return Optional.empty();
        }
    }
}
