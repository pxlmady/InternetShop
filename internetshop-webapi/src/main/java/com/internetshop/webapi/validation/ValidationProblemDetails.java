package com.internetshop.webapi.validation;

import com.internetshop.application.shared.validation.ValidationException;
import com.internetshop.application.shared.validation.ValidationFailure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ПР8: структурована відповідь на помилку валідації — аналог ValidationProblemDetails, який ASP.NET Core
 * повертає при автоматичній валідації FluentValidation. Приклад тіла відповіді 400:
 * <pre>
 * {"title":"Вхідні дані не пройшли перевірку.","status":400,
 *  "error":"Назва товару обов'язкова. Потрібно вказати ціну товару.",
 *  "errors":{"name":["Назва товару обов'язкова."],"price":["Потрібно вказати ціну товару."]}}
 * </pre>
 * errors групує повідомлення за полями запиту, щоб клієнт міг показати їх біля відповідних полів форми.
 * error — ті самі повідомлення одним рядком, у форматі решти помилок API ({"error": "..."}).
 * Назовні йдуть лише назви полів запиту й тексти правил: ні значень, які надіслав клієнт, ні внутрішніх даних.
 */
public record ValidationProblemDetails(String title, int status, String error, Map<String, List<String>> errors) {

    public static final String TITLE = "Вхідні дані не пройшли перевірку.";

    public static ValidationProblemDetails from(ValidationException exception) {
        Map<String, List<String>> byField = new LinkedHashMap<>();
        for (ValidationFailure failure : exception.errors())
            byField.computeIfAbsent(failure.propertyName(), k -> new ArrayList<>()).add(failure.errorMessage());
        return new ValidationProblemDetails(TITLE, 400, exception.getMessage(), byField);
    }
}
