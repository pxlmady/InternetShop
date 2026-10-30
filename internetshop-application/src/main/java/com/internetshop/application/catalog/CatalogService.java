package com.internetshop.application.catalog;

import com.internetshop.application.shared.UnitOfWork;
import com.internetshop.domain.catalog.Product;
import com.internetshop.domain.catalog.ProductName;
import com.internetshop.domain.shared.DomainRuleException;
import com.internetshop.domain.shared.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Сценарії контексту Catalog. Для інших контекстів відкритий лише через вузький контракт
 * ProductQueries (читання), а не весь клас разом зі створенням і видаленням (ПР3, ISP).
 * Перетворення на DTO винесено в ProductMapper (ПР3, SRP).
 * <p>
 * ПР4 (DI): реалізує абстракцію CatalogUseCases; обидві залежності (сховище і мапер) — інтерфейси,
 * передані через конструктор. Сервіс без стану, у контейнері — Scoped (один екземпляр на HTTP-запит).
 * <p>
 * ПР5 (Repository + Unit of Work): товари читаються й змінюються через ProductRepository,
 * а записуються одним викликом unitOfWork.saveChanges() наприкінці сценарію.
 */
public final class CatalogService implements CatalogUseCases {

    private final ProductRepository products;
    private final UnitOfWork unitOfWork;
    private final ProductMapper mapper;

    public CatalogService(ProductRepository products, UnitOfWork unitOfWork, ProductMapper mapper) {
        this.products = Objects.requireNonNull(products);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<ProductDto> list() {
        return products.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public ProductDto get(UUID id) {
        if (id == null) throw new ProductNotFoundException();
        return mapper.toDto(products.findById(id).orElseThrow(ProductNotFoundException::new));
    }

    @Override
    public ProductDto create(String name, String description, BigDecimal price) {
        if (price == null) throw new DomainRuleException("Потрібно вказати ціну товару.");
        // Бізнес-правила перевіряють ProductName, Money та Product.
        Product product = new Product(new ProductName(name), description, new Money(price));
        products.add(product);
        unitOfWork.saveChanges();
        return mapper.toDto(product);
    }

    /**
     * Видаляє товар з каталогу. Уже сформовані замовлення не змінюються: контекст Ordering
     * зберігає власний «знімок» назви й ціни, а додати видалений товар до нового замовлення не можна.
     */
    @Override
    public void delete(UUID id) {
        if (id == null || products.findById(id).isEmpty()) throw new ProductNotFoundException();
        products.remove(id);
        unitOfWork.saveChanges();
    }
}
