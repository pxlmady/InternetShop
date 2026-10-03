package com.internetshop.domain.ordering;

/** Життєвий цикл замовлення в межах цієї роботи. */
public enum OrderStatus {
    /** Чернетка: можна додавати товари. */
    DRAFT,
    /** Оформлене: склад зафіксовано, зміни заборонені. */
    PLACED
}
