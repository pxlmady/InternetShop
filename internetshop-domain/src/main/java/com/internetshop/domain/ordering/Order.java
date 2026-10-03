package com.internetshop.domain.ordering;

import com.internetshop.domain.shared.DomainRuleException;
import com.internetshop.domain.shared.Entity;
import com.internetshop.domain.shared.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bounded Context «Ordering». Замовлення — корінь агрегату: усі зміни рядків проходять
 * через його методи, тому інваріанти завжди перевіряються в одному місці:
 * <ul>
 *   <li>змінювати можна лише чернетку (DRAFT);</li>
 *   <li>кількість одного товару — від 1 до 99 (повторне додавання сумує кількість);</li>
 *   <li>порожнє замовлення оформити не можна.</li>
 * </ul>
 */
public final class Order extends Entity {

    private final CustomerId customerId;
    private final List<OrderLine> lines;
    private OrderStatus status;

    /** Версія збереженого стану; сховище використовує її для виявлення одночасних змін. */
    private final int version;

    public Order(CustomerId customerId) {
        super();
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.lines = new ArrayList<>();
        this.status = OrderStatus.DRAFT;
        this.version = 0;
    }

    private Order(UUID id, CustomerId customerId, OrderStatus status, List<OrderLine> lines, int version) {
        super(id);
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.status = Objects.requireNonNull(status, "status");
        this.lines = new ArrayList<>(lines);
        this.version = version;
    }

    /** Відновлення збереженого замовлення. Використовується лише сховищем. */
    public static Order restore(UUID id, CustomerId customerId, OrderStatus status, List<OrderLine> lines, int version) {
        return new Order(id, customerId, status, lines, version);
    }

    /** Додає товар до чернетки. Якщо товар уже є — збільшує його кількість. */
    public void addItem(UUID productId, String productName, Money unitPrice, Quantity quantity) {
        ensureDraft();
        Objects.requireNonNull(quantity, "quantity");
        ensureTotalWithinLimit(unitPrice, quantity);
        for (OrderLine line : lines) {
            if (line.getProductId().equals(productId)) {
                line.increase(quantity); // Quantity сам перевіряє межу 99
                return;
            }
        }
        lines.add(new OrderLine(productId, productName, unitPrice, quantity));
    }

    /** Оформлює замовлення: після цього склад змінювати не можна. */
    public void place() {
        ensureDraft();
        if (lines.isEmpty())
            throw new DomainRuleException("Не можна оформити порожнє замовлення.");
        status = OrderStatus.PLACED;
    }

    public Money total() {
        Money sum = Money.ZERO;
        for (OrderLine line : lines) sum = sum.add(line.lineTotal());
        return sum;
    }

    /** Перевірка до зміни стану: агрегат не повинен опинитися в недопустимому стані. */
    private void ensureTotalWithinLimit(Money unitPrice, Quantity quantity) {
        if (unitPrice == null) return; // відсутню ціну відхилить OrderLine
        try {
            total().add(unitPrice.multiply(quantity.value()));
        } catch (DomainRuleException e) {
            throw new DomainRuleException("Сума замовлення перевищує допустиму.");
        }
    }

    private void ensureDraft() {
        if (status != OrderStatus.DRAFT)
            throw new DomainRuleException("Замовлення вже оформлене, змінювати його не можна.");
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    /** Колекція лише для читання: змінити рядки можна тільки через методи Order. */
    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public int getVersion() {
        return version;
    }
}
