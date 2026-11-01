package com.internetshop.webapi.validation;

import com.internetshop.application.shared.validation.AbstractValidator;
import com.internetshop.webapi.di.ServiceCollection;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ПР8: реєстрація валідаторів у DI-контейнері — аналог
 * builder.Services.AddValidatorsFromAssemblyContaining&lt;CreateOrderCommandValidator&gt;() + AddFluentValidationAutoValidation().
 * <p>
 * Для кожного валідатора тип моделі визначається автоматично з його оголошення
 * (CreateOrderRequestValidator extends AbstractValidator&lt;CreateOrderRequest&gt; → CreateOrderRequest),
 * сам валідатор реєструється як Scoped, а відповідність «модель → валідатор» потрапляє в AutoValidation (Singleton).
 * Сканування цілого пакета (як «assembly scanning» у .NET) не використовуємо: явний перелік простіший і
 * зрозуміліший, а помилка (два валідатори для однієї моделі) виявляється одразу під час старту.
 */
public final class ValidationServices {

    private ValidationServices() { }

    @SafeVarargs
    public static ServiceCollection addValidators(ServiceCollection services,
                                                  Class<? extends AbstractValidator<?>>... validatorTypes) {
        Objects.requireNonNull(services);
        Map<Class<?>, Class<?>> byModel = new LinkedHashMap<>();
        for (Class<? extends AbstractValidator<?>> validatorType : validatorTypes) {
            Class<?> modelType = modelTypeOf(validatorType);
            if (byModel.putIfAbsent(modelType, validatorType) != null)
                throw new IllegalArgumentException("Для " + modelType.getSimpleName() + " уже зареєстровано валідатор.");
            registerScoped(services, validatorType);
        }
        return services.addSingleton(AutoValidation.class, s -> new AutoValidation(byModel));
    }

    /** T з «extends AbstractValidator&lt;T&gt;». */
    static Class<?> modelTypeOf(Class<?> validatorType) {
        Type parent = validatorType.getGenericSuperclass();
        if (parent instanceof ParameterizedType p && p.getRawType() == AbstractValidator.class
                && p.getActualTypeArguments()[0] instanceof Class<?> model)
            return model;
        throw new IllegalArgumentException(validatorType.getSimpleName() + " має безпосередньо наслідувати AbstractValidator<Модель>.");
    }

    private static <V> void registerScoped(ServiceCollection services, Class<V> validatorType) {
        services.addScoped(validatorType, validatorType);
    }
}
