package com.internetshop.infrastructure.integration;

import com.internetshop.application.catalog.ProductDto;
import com.internetshop.application.catalog.ProductNotFoundException;
import com.internetshop.application.catalog.ProductQueries;
import com.internetshop.application.ordering.CatalogProduct;
import com.internetshop.application.ordering.ProductCatalog;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Адаптер між bounded contexts (антикорупційний шар): реалізує контракт Ordering (ProductCatalog)
 * через публічний контракт Catalog і перекладає ProductDto у CatalogProduct.
 * Якщо Catalog колись стане окремим сервісом, зміниться лише цей клас.
 * <p>
 * ПР3: залежить від абстракції ProductQueries (DIP), а не від конкретного CatalogService,
 * і бачить лише читання каталогу (ISP) — створювати чи видаляти товари адаптер не може.
 */
public final class CatalogProductAdapter implements ProductCatalog {

    private final ProductQueries catalog;

    public CatalogProductAdapter(ProductQueries catalog) {
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
