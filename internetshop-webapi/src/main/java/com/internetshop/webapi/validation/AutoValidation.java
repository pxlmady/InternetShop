package com.internetshop.webapi.validation;

import com.internetshop.application.shared.validation.Validator;
import com.internetshop.webapi.di.ServiceScope;
import com.internetshop.webapi.http.Router;

import java.util.Map;
import java.util.Objects;

/**
 * ПР8: автоматична валідація вхідних моделей — аналог AddFluentValidationAutoValidation() в ASP.NET Core.
 * <p>
 * Router викликає її для кожного запиту з тілом: після розбору JSON у DTO (model binding) і до виклику ендпоінта,
 * тобто ДО виконання use-case. За типом DTO знаходиться зареєстрований валідатор, береться з області (scope)
 * поточного запиту (як і в FluentValidation, валідатори Scoped — вони можуть залежати від репозиторіїв)
 * і запускається. Якщо є помилки — ValidationException, і ні ендпоінт, ні сценарій не виконуються.
 * <p>
 * Ендпоінти й сервіси про валідацію не знають: новий валідатор = новий клас + один рядок реєстрації.
 */
public final class AutoValidation implements Router.ModelValidator {

    /** Тип вхідної моделі (DTO) → клас її валідатора. */
    private final Map<Class<?>, Class<?>> validators;

    AutoValidation(Map<Class<?>, Class<?>> validators) {
        this.validators = Map.copyOf(validators);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void validate(ServiceScope scope, Object model) {
        Objects.requireNonNull(model);
        Class<?> validatorType = validators.get(model.getClass());
        if (validatorType == null) return; // для моделі без валідатора додаткових перевірок немає
        Validator<Object> validator = (Validator<Object>) scope.get(validatorType);
        validator.validateAndThrow(model);
    }

    /** Чи є валідатор для типу моделі (для перевірок конфігурації). */
    public boolean hasValidatorFor(Class<?> modelType) {
        return validators.containsKey(modelType);
    }
}
