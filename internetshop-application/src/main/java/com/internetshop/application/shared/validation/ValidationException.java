package com.internetshop.application.shared.validation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ПР8: вхідні дані не пройшли перевірку (аналог FluentValidation.ValidationException).
 * Несе всі помилки разом, щоб клієнт отримав їх в одній структурованій відповіді, а не по одній.
 * Use-case у цьому випадку не виконується взагалі.
 */
public final class ValidationException extends RuntimeException {

    private final List<ValidationFailure> errors;

    public ValidationException(List<ValidationFailure> errors) {
        super(errors.stream().map(ValidationFailure::errorMessage).collect(Collectors.joining(" ")));
        if (errors.isEmpty()) throw new IllegalArgumentException("ValidationException без помилок не має сенсу.");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationFailure> errors() {
        return errors;
    }
}
