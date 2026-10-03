package com.internetshop.domain.shared;

import java.math.BigDecimal;

/**
 * Value Object «Гроші» (у гривнях). Незмінний, рівність за значенням: 10 == 10.00.
 * Спільне ядро (Shared Kernel): однаково розуміється і в Catalog, і в Ordering.
 */
public record Money(BigDecimal amount) {

    /** Верхня межа суми: захищає від помилок введення та від надвеликих чисел. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        if (amount == null)
            throw new DomainRuleException("Потрібно вказати суму.");
        if (amount.signum() < 0)
            throw new DomainRuleException("Сума не може бути від'ємною.");
        if (amount.compareTo(MAX_AMOUNT) > 0)
            throw new DomainRuleException("Сума не може перевищувати " + MAX_AMOUNT.toPlainString() + ".");
        if (amount.stripTrailingZeros().scale() > 2)
            throw new DomainRuleException("Сума може містити не більше двох знаків після коми.");
        amount = amount.setScale(2); // нормалізація: однакові суми мають однакове представлення
    }

    public static Money of(String amount) {
        try {
            return new Money(new BigDecimal(amount));
        } catch (NumberFormatException | NullPointerException e) {
            throw new DomainRuleException("Некоректна сума.");
        }
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money multiply(int times) {
        return new Money(amount.multiply(BigDecimal.valueOf(times)));
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
