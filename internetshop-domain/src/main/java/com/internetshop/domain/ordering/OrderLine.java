package com.internetshop.domain.ordering;

import com.internetshop.domain.shared.DomainRuleException;
import com.internetshop.domain.shared.Entity;
import com.internetshop.domain.shared.Money;

import java.util.Objects;
import java.util.UUID;

/**
 * Рядок замовлення — сутність усередині агрегату Order. Створюється та змінюється лише через Order.
 * Зберігає «знімок» товару на момент додавання (назва й ціна), тому подальші зміни
 * в каталозі не змінюють уже сформоване замовлення. Це «товар» у розумінні Ordering,
 * а не сутність Product з контексту Catalog.
 */
public final class OrderLine extends Entity {

    private final UUID productId;
    private final String productName;
    private final Money unitPrice;
    private Quantity quantity;

    OrderLine(UUID productId, String productName, Money unitPrice, Quantity quantity) {
        this(UUID.randomUUID(), productId, productName, unitPrice, quantity);
    }

    private OrderLine(UUID id, UUID productId, String productName, Money unitPrice, Quantity quantity) {
        super(id);
        if (productId == null)
            throw new DomainRuleException("Потрібен ідентифікатор товару.");
        if (productName == null || productName.isBlank())
            throw new DomainRuleException("Потрібна назва товару.");
        if (unitPrice == null || !unitPrice.isPositive())
            throw new DomainRuleException("Ціна товару має бути більшою за нуль.");
        this.productId = productId;
        this.productName = productName.trim();
        this.unitPrice = unitPrice;
        this.quantity = Objects.requireNonNull(quantity, "quantity");
    }

    /** Відновлення зі сховища; використовується разом з Order.restore. */
    public static OrderLine restore(UUID id, UUID productId, String productName, Money unitPrice, Quantity quantity) {
        return new OrderLine(id, productId, productName, unitPrice, quantity);
    }

    /** Доступно лише в пакеті: кількість змінює корінь агрегату. */
    void increase(Quantity extra) {
        quantity = quantity.plus(extra);
    }

    public Money lineTotal() {
        return unitPrice.multiply(quantity.value());
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public Quantity getQuantity() {
        return quantity;
    }
}
