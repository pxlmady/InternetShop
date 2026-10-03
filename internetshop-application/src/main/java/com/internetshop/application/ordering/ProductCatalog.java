package com.internetshop.application.ordering;

import java.util.Optional;
import java.util.UUID;

/**
 * Контракт взаємодії Ordering → Catalog. Визначений на боці Ordering його власною мовою.
 * Реалізація (CatalogProductAdapter в Infrastructure) перекладає дані Catalog у CatalogProduct,
 * тому Ordering не залежить від класів іншого bounded context.
 */
public interface ProductCatalog {

    Optional<CatalogProduct> findProduct(UUID productId);
}
