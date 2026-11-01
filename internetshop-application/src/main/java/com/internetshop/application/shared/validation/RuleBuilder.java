package com.internetshop.application.shared.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * ПР8: ланцюжок перевірок однієї властивості (аналог IRuleBuilder з FluentValidation).
 * <p>
 * Вбудовані перевірки (notNull, notEmpty, maximumLength, greaterThan, ...) — синтаксичні: формат, порожні значення,
 * діапазони. must(...) — довільна умова: через неї описуються семантичні правила, зокрема ті, що потребують доступу
 * до даних (валідатор отримує репозиторій чи інший контракт через конструктор, як і будь-який компонент у DI).
 * <p>
 * Як у FluentValidation, перевірки довжини й порівняння пропускають null: за наявність значення відповідає
 * notNull/notEmpty. Перевірки однієї властивості виконуються по черзі до першої невдалої (CascadeMode.Stop),
 * щоб, наприклад, для порожньої назви не показувати ще й «такий товар уже є».
 */
public final class RuleBuilder<T, P> {

    private static final class Check<P> {
        final Predicate<P> test;
        String message;

        Check(Predicate<P> test, String message) {
            this.test = test;
            this.message = message;
        }
    }

    private final String propertyName;
    private final Function<T, P> property;
    private final List<Check<P>> checks = new ArrayList<>();
    private Predicate<T> condition = instance -> true;

    RuleBuilder(String propertyName, Function<T, P> property) {
        this.propertyName = Objects.requireNonNull(propertyName);
        this.property = Objects.requireNonNull(property);
    }

    // ---- синтаксичні перевірки ----

    public RuleBuilder<T, P> notNull() {
        return add(Objects::nonNull, "Поле '" + propertyName + "' обов'язкове.");
    }

    /** null, порожній або з самих пробілів рядок, порожня колекція — помилка (як NotEmpty у FluentValidation). */
    public RuleBuilder<T, P> notEmpty() {
        return add(v -> v != null && !(v instanceof String s && s.isBlank()) && !(v instanceof Collection<?> c && c.isEmpty()),
                "Поле '" + propertyName + "' не може бути порожнім.");
    }

    public RuleBuilder<T, P> notEqual(P forbidden) {
        return add(v -> v == null || !v.equals(forbidden), "Поле '" + propertyName + "' має недопустиме значення.");
    }

    public RuleBuilder<T, P> minimumLength(int min) {
        return add(v -> v == null || v.toString().length() >= min,
                "Поле '" + propertyName + "' має містити щонайменше " + min + " символів.");
    }

    public RuleBuilder<T, P> maximumLength(int max) {
        return add(v -> v == null || v.toString().length() <= max,
                "Поле '" + propertyName + "' має містити не більше " + max + " символів.");
    }

    public RuleBuilder<T, P> greaterThan(P limit) {
        return add(v -> v == null || compare(v, limit) > 0, "Поле '" + propertyName + "' має бути більшим за " + limit + ".");
    }

    public RuleBuilder<T, P> greaterThanOrEqualTo(P limit) {
        return add(v -> v == null || compare(v, limit) >= 0, "Поле '" + propertyName + "' має бути не меншим за " + limit + ".");
    }

    public RuleBuilder<T, P> lessThanOrEqualTo(P limit) {
        return add(v -> v == null || compare(v, limit) <= 0, "Поле '" + propertyName + "' має бути не більшим за " + limit + ".");
    }

    public RuleBuilder<T, P> inclusiveBetween(P from, P to) {
        return add(v -> v == null || (compare(v, from) >= 0 && compare(v, to) <= 0),
                "Поле '" + propertyName + "' має бути від " + from + " до " + to + ".");
    }

    // ---- довільна (семантична) умова ----

    /** Аналог Must(...): умова над значенням властивості; значення null сюди не передається. */
    public RuleBuilder<T, P> must(Predicate<P> predicate) {
        Objects.requireNonNull(predicate);
        return add(v -> v == null || predicate.test(v), "Поле '" + propertyName + "' має некоректне значення.");
    }

    // ---- налаштування правила ----

    /** Замінює повідомлення останньої доданої перевірки (аналог WithMessage). */
    public RuleBuilder<T, P> withMessage(String message) {
        if (checks.isEmpty()) throw new IllegalStateException("withMessage() викликано до будь-якої перевірки.");
        checks.get(checks.size() - 1).message = Objects.requireNonNull(message);
        return this;
    }

    /** Правило перевіряється лише за умови над усією моделлю (аналог When). */
    public RuleBuilder<T, P> when(Predicate<T> condition) {
        this.condition = Objects.requireNonNull(condition);
        return this;
    }

    void validate(T instance, List<ValidationFailure> failures) {
        if (!condition.test(instance)) return;
        P value = property.apply(instance);
        for (Check<P> check : checks) {
            if (!check.test.test(value)) {
                failures.add(new ValidationFailure(propertyName, check.message));
                return; // CascadeMode.Stop у межах однієї властивості
            }
        }
    }

    private RuleBuilder<T, P> add(Predicate<P> test, String defaultMessage) {
        checks.add(new Check<>(test, defaultMessage));
        return this;
    }

    @SuppressWarnings("unchecked")
    private static <P> int compare(P value, P limit) {
        if (!(value instanceof Comparable<?>))
            throw new IllegalStateException("Порівняння можливе лише для Comparable-властивостей.");
        return ((Comparable<P>) value).compareTo(limit);
    }
}
