package com.internetshop.webapi.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * Правила «тип винятку → HTTP-статус» (ПР3, SRP + OCP).
 * Раніше ці правила були жорстко записані в Router ланцюжком catch: новий тип помилки вимагав
 * змінювати Router. Тепер Router закритий для модифікації, а нове правило додається одним викликом
 * map(...) у точці складання (InternetShopApp).
 * <p>
 * Правило для базового типу діє й для всіх його підтипів (LSP): ProductNotFoundException
 * та OrderNotFoundException обробляються одним правилом для NotFoundException.
 * <p>
 * ПР8: правило може задавати і тіло відповіді. Так помилка валідації повертається структуровано
 * (помилки за полями), а Router для цього не змінюється — знову OCP.
 */
public final class ErrorMapper {

    private record Rule(Class<? extends RuntimeException> type, int status, Function<RuntimeException, Object> body) { }

    private final List<Rule> rules = new ArrayList<>();

    /** Додає правило з тілом за замовчуванням {"error": "повідомлення"}. Спрацьовує перше правило, що підходить. */
    public ErrorMapper map(Class<? extends RuntimeException> type, int status) {
        return add(type, status, ErrorMapper::defaultBody);
    }

    /** ПР8: правило з власним тілом відповіді (наприклад, ValidationProblemDetails для помилок валідації). */
    public <E extends RuntimeException> ErrorMapper map(Class<E> type, int status, Function<? super E, ?> body) {
        Objects.requireNonNull(body);
        return add(type, status, e -> body.apply(type.cast(e)));
    }

    /** Статус для винятку або порожньо, якщо помилка неочікувана (тоді Router відповідає 500). */
    public OptionalInt statusFor(RuntimeException error) {
        Rule rule = ruleFor(error);
        return rule == null ? OptionalInt.empty() : OptionalInt.of(rule.status());
    }

    /** Тіло відповіді для очікуваної помилки (для неочікуваної — лише загальне повідомлення). */
    public Object bodyFor(RuntimeException error) {
        Rule rule = ruleFor(error);
        return rule == null ? Map.of("error", "Внутрішня помилка сервера.") : rule.body().apply(error);
    }

    private ErrorMapper add(Class<? extends RuntimeException> type, int status, Function<RuntimeException, Object> body) {
        if (status < 400 || status > 499)
            throw new IllegalArgumentException("Очікувану помилку можна відобразити лише на статус 4xx.");
        rules.add(new Rule(Objects.requireNonNull(type), status, body));
        return this;
    }

    private Rule ruleFor(RuntimeException error) {
        for (Rule r : rules) {
            if (r.type().isInstance(error)) return r;
        }
        return null;
    }

    private static Object defaultBody(RuntimeException error) {
        return Map.of("error", Objects.requireNonNullElse(error.getMessage(), ""));
    }
}
