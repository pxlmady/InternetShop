package com.internetshop.application.catalog;

import com.internetshop.domain.catalog.Product;
import com.internetshop.domain.catalog.ProductName;
import com.internetshop.domain.shared.DomainRuleException;
import com.internetshop.domain.shared.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Сценарії контексту Catalog. Також є публічною точкою входу (контрактом)
 * для інших контекстів, яким потрібні дані про товари.
 */
public final class CatalogService {

    private final ProductStore store;

    public CatalogService(ProductStore store) {
        this.store = Objects.requireNonNull(store);
    }

    public List<ProductDto> list() {
        return store.findAll().stream().map(CatalogService::toDto).toList();
    }

    public ProductDto get(UUID id) {
        if (id == null) throw new ProductNotFoundException();
        return toDto(store.findById(id).orElseThrow(ProductNotFoundException::new));
    }

    public ProductDto create(String name, String description, BigDecimal price) {
        if (price == null) throw new DomainRuleException("Потрібно вказати ціну товару.");
        // Бізнес-правила перевіряють ProductName, Money та Product.
        Product product = new Product(new ProductName(name), description, new Money(price));
        store.add(product);
        return toDto(product);
    }

    /**
     * Видаляє товар з каталогу. Уже сформовані замовлення не змінюються: контекст Ordering
     * зберігає власний «знімок» назви й ціни, а додати видалений товар до нового замовлення не можна.
     */
    public void delete(UUID id) {
        if (id == null || store.findById(id).isEmpty()) throw new ProductNotFoundException();
        store.remove(id);
    }

    private static ProductDto toDto(Product p) {
        return new ProductDto(p.getId(), p.getName().value(), p.getDescription(), p.getPrice().amount());
    }
}
