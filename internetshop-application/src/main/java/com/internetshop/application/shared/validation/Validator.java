package com.internetshop.application.shared.validation;

/**
 * ПР8 (валідація): абстракція валідатора вхідної моделі — аналог IValidator&lt;T&gt; з FluentValidation.
 * Конвеєр WebApi знаходить валідатор за типом DTO і запускає його автоматично, до виконання use-case.
 */
public interface Validator<T> {

    /** Перевіряє модель і повертає всі знайдені помилки (не зупиняється на першій). */
    ValidationResult validate(T instance);

    /** Як ValidateAndThrow у FluentValidation: якщо є помилки — ValidationException з усіма ними. */
    default void validateAndThrow(T instance) {
        ValidationResult result = validate(instance);
        if (!result.isValid()) throw new ValidationException(result.errors());
    }
}
