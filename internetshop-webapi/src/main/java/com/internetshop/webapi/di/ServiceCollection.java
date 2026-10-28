package com.internetshop.webapi.di;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Реєстрація залежностей (аналог builder.Services в ASP.NET Core): для кожної абстракції вказуються
 * реалізація і життєвий цикл. Після build() набір реєстрацій не змінюється.
 * <pre>
 * services.addSingleton(EventBus.class, InProcessEventBus.class);  // AddSingleton&lt;IEventBus, InProcessEventBus&gt;()
 * services.addScoped(OrderUseCases.class, OrderService.class);     // AddScoped&lt;...&gt;()
 * services.addTransient(OrderMapper.class, OrderDtoMapper.class);  // AddTransient&lt;IOrderMapper, OrderMapper&gt;()
 * </pre>
 * Реалізацію, заданої класом, контейнер створює через її єдиний public-конструктор (constructor injection):
 * кожен параметр конструктора отримується з контейнера за типом.
 */
public final class ServiceCollection {

    /** Фабрика для випадків, коли потрібні значення конфігурації або той самий екземпляр під кількома інтерфейсами. */
    @FunctionalInterface
    public interface Factory<T> {
        T create(ServiceResolver services);
    }

    record Registration(Class<?> type, Lifetime lifetime, Factory<?> factory) { }

    private final Map<Class<?>, Registration> registrations = new LinkedHashMap<>();

    public <T> ServiceCollection addSingleton(Class<T> type, Class<? extends T> implementation) {
        return add(type, Lifetime.SINGLETON, byConstructor(implementation));
    }

    public <T> ServiceCollection addSingleton(Class<T> type, Factory<? extends T> factory) {
        return add(type, Lifetime.SINGLETON, factory);
    }

    public <T> ServiceCollection addScoped(Class<T> type, Class<? extends T> implementation) {
        return add(type, Lifetime.SCOPED, byConstructor(implementation));
    }

    public <T> ServiceCollection addScoped(Class<T> type, Factory<? extends T> factory) {
        return add(type, Lifetime.SCOPED, factory);
    }

    public <T> ServiceCollection addTransient(Class<T> type, Class<? extends T> implementation) {
        return add(type, Lifetime.TRANSIENT, byConstructor(implementation));
    }

    public <T> ServiceCollection addTransient(Class<T> type, Factory<? extends T> factory) {
        return add(type, Lifetime.TRANSIENT, factory);
    }

    public ServiceProvider build() {
        return new ServiceProvider(Map.copyOf(registrations));
    }

    private ServiceCollection add(Class<?> type, Lifetime lifetime, Factory<?> factory) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(factory);
        if (type == ServiceScopeFactory.class)
            throw new IllegalArgumentException("ServiceScopeFactory надає сам контейнер.");
        // Повторна реєстрація найчастіше є помилкою конфігурації, тому вона заборонена явно.
        if (registrations.putIfAbsent(type, new Registration(type, lifetime, factory)) != null)
            throw new IllegalArgumentException("Тип уже зареєстровано: " + type.getSimpleName());
        return this;
    }

    /** Конструктор шукається одразу під час реєстрації, щоб помилка конфігурації проявилась при запуску. */
    private static <T> Factory<T> byConstructor(Class<T> implementation) {
        Objects.requireNonNull(implementation);
        if (implementation.isInterface() || Modifier.isAbstract(implementation.getModifiers()))
            throw new IllegalArgumentException("Потрібен конкретний клас: " + implementation.getSimpleName());
        Constructor<?>[] constructors = implementation.getConstructors();
        if (constructors.length != 1)
            throw new IllegalArgumentException(implementation.getSimpleName() + " має мати рівно один public-конструктор");
        Constructor<?> constructor = constructors[0];
        return services -> {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            Object[] arguments = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) arguments[i] = services.get(parameterTypes[i]);
            try {
                return implementation.cast(constructor.newInstance(arguments));
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                throw new DependencyResolutionException("Не вдалося створити " + implementation.getSimpleName(), e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new DependencyResolutionException("Не вдалося створити " + implementation.getSimpleName(), e);
            }
        };
    }
}
