package com.internetshop.application.shared.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * ПР8 (валідація): базовий клас декларативного валідатора — Java-аналог AbstractValidator&lt;T&gt; з FluentValidation.
 * <p>
 * Конкретний валідатор лише описує правила у своєму конструкторі:
 * <pre>
 * ruleFor("customerId", CreateOrderRequest::customerId)
 *         .notNull().withMessage("...")
 *         .notEqual(EMPTY).withMessage("...");
 * </pre>
 * У C# назву властивості FluentValidation дістає з виразу x =&gt; x.Customer; у Java лямбда назви поля не знає,
 * тому назву (як у JSON запиту) передаємо першим аргументом — саме вона потрапляє у відповідь з помилками.
 * <p>
 * validate() проходить усі правила і збирає всі помилки, а не лише першу: клієнт одразу бачить,
 * що саме виправити. Сторонньої бібліотеки немає (як і власний DI-контейнер у ПР4): у Java немає «офіційного»
 * FluentValidation, тому мінімальний аналог з тим самим API написано в Application.
 */
public abstract class AbstractValidator<T> implements Validator<T> {

    private final List<RuleBuilder<T, ?>> rules = new ArrayList<>();

    /** Починає правило для однієї властивості моделі (аналог RuleFor(x =&gt; x.Property)). */
    protected final <P> RuleBuilder<T, P> ruleFor(String propertyName, Function<T, P> property) {
        RuleBuilder<T, P> rule = new RuleBuilder<>(propertyName, property);
        rules.add(rule);
        return rule;
    }

    @Override
    public final ValidationResult validate(T instance) {
        Objects.requireNonNull(instance, "instance");
        List<ValidationFailure> failures = new ArrayList<>();
        for (RuleBuilder<T, ?> rule : rules) rule.validate(instance, failures);
        return new ValidationResult(failures);
    }
}
