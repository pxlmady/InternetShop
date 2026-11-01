package com.internetshop.application.shared.validation;

import java.util.List;

/** ПР8: результат перевірки моделі (аналог ValidationResult з FluentValidation). */
public record ValidationResult(List<ValidationFailure> errors) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }
}
