package com.internetshop.domain.ordering;

import com.internetshop.domain.shared.DomainRuleException;

/** Value Object: кількість одиниць одного товару в замовленні (1–99). */
public record Quantity(int value) {

    public static final int MAX = 99;

    public Quantity {
        if (value < 1 || value > MAX)
            throw new DomainRuleException("Кількість одного товару має бути від 1 до " + MAX + ".");
    }

    public Quantity plus(Quantity other) {
        return new Quantity(value + other.value);
    }
}
