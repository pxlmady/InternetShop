package com.internetshop.webapi.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Правила «тип винятку → HTTP-статус» (ПР3, SRP + OCP).
 * Раніше ці правила були жорстко записані в Router ланцюжком catch: новий тип помилки вимагав
 * змінювати Router. Тепер Router закритий для модифікації, а нове правило додається одним викликом
 * map(...) у точці складання (InternetShopApp).
 * <p>
 * Правило для базового типу діє й для всіх його підтипів (LSP): ProductNotFoundException
 * та OrderNotFoundException обробляються одним правилом для NotFoundException.
 */
public final class ErrorMapper {

    private record Rule(Class<? extends RuntimeException> type, int status) { }

    private final List<Rule> rules = new ArrayList<>();

    /** Додає правило. Перевіряються в порядку додавання; спрацьовує перше, що підходить. */
    public ErrorMapper map(Class<? extends RuntimeException> type, int status) {
        if (status < 400 || status > 499)
            throw new IllegalArgumentException("Очікувану помилку можна відобразити лише на статус 4xx.");
        rules.add(new Rule(Objects.requireNonNull(type), status));
        return this;
    }

    /** Статус для винятку або порожньо, якщо помилка неочікувана (тоді Router відповідає 500). */
    public OptionalInt statusFor(RuntimeException error) {
        for (Rule r : rules) {
            if (r.type().isInstance(error)) return OptionalInt.of(r.status());
        }
        return OptionalInt.empty();
    }
}
