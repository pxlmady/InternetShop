package com.internetshop.application.shared.validation;

import java.util.Objects;

/** ПР8: одна помилка валідації — назва поля вхідної моделі і зрозуміле користувачу API повідомлення. */
public record ValidationFailure(String propertyName, String errorMessage) {

    public ValidationFailure {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(errorMessage);
    }
}
