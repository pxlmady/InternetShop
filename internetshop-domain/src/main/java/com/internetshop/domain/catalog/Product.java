package com.internetshop.domain.catalog;

import com.internetshop.domain.shared.DomainRuleException;
import com.internetshop.domain.shared.Entity;
import com.internetshop.domain.shared.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Bounded Context «Catalog». Товар — сутність і корінь власного агрегату.
 * У Catalog товар описується для вітрини: назва, опис, поточна ціна.
 */
public final class Product extends Entity {

    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    private final ProductName name;
    private final String description;
    private final Money price;

    public Product(ProductName name, String description, Money price) {
        this(UUID.randomUUID(), name, description, price);
    }

    private Product(UUID id, ProductName name, String description, Money price) {
        super(id);
        this.name = Objects.requireNonNull(name, "name");
        String normalized = description == null ? "" : description.trim();
        if (normalized.length() > MAX_DESCRIPTION_LENGTH)
            throw new DomainRuleException("Опис товару не може перевищувати " + MAX_DESCRIPTION_LENGTH + " символів.");
        if (price == null || !price.isPositive())
            throw new DomainRuleException("Ціна товару має бути більшою за нуль.");
        this.description = normalized;
        this.price = price;
    }

    /** Відновлення збереженого товару. Правила перевіряються повторно тим самим конструктором. */
    public static Product restore(UUID id, ProductName name, String description, Money price) {
        return new Product(id, name, description, price);
    }

    public ProductName getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return price;
    }
}
