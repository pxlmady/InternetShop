package com.internetshop.webapi.di;

import com.internetshop.webapi.di.ServiceCollection.Registration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Кореневий контейнер (аналог IServiceProvider, отриманого з builder.Build()): створює залежності
 * за реєстраціями ServiceCollection і стежить за їхнім життєвим циклом.
 * <ul>
 *   <li>SINGLETON — створюється один раз і зберігається тут;</li>
 *   <li>SCOPED — зберігається в ServiceScope; з кореневого контейнера отримати його не можна;</li>
 *   <li>TRANSIENT — щоразу новий екземпляр.</li>
 * </ul>
 * Правило з методички «Singleton не повинен залежати від Scoped» перевіряється автоматично:
 * singleton створюється через кореневий контейнер, тому спроба отримати для нього scoped-залежність
 * завершується DependencyResolutionException ще під час запуску застосунку.
 */
public final class ServiceProvider implements ServiceResolver, ServiceScopeFactory {

    private final Map<Class<?>, Registration> registrations;
    private final Map<Class<?>, Object> singletons = new HashMap<>();
    /** Ланцюжок типів, які зараз створюються в цьому потоці: для виявлення циклів і зрозумілих повідомлень. */
    private final ThreadLocal<Deque<Class<?>>> resolving = ThreadLocal.withInitial(ArrayDeque::new);

    ServiceProvider(Map<Class<?>, Registration> registrations) {
        this.registrations = registrations;
    }

    @Override
    public <T> T get(Class<T> type) {
        return resolve(type, null);
    }

    @Override
    public ServiceScope createScope() {
        return new ServiceScope(this);
    }

    /** Чи зареєстровано тип (для перевірок конфігурації). */
    public boolean isRegistered(Class<?> type) {
        return registrations.containsKey(type);
    }

    public Lifetime lifetimeOf(Class<?> type) {
        Registration r = registrations.get(type);
        if (r == null) throw new DependencyResolutionException("Тип не зареєстровано в контейнері: " + type.getSimpleName());
        return r.lifetime();
    }

    /** @param scope область запиту або null, якщо залежність потрібна кореневому контейнеру (singleton). */
    <T> T resolve(Class<T> type, ServiceScope scope) {
        if (type == ServiceScopeFactory.class) return type.cast(this);
        Registration r = registrations.get(type);
        if (r == null) throw new DependencyResolutionException("Тип не зареєстровано в контейнері: " + type.getSimpleName());
        if (resolving.get().contains(type))
            throw new DependencyResolutionException("Циклічна залежність: " + chainTo(type));
        Object instance = switch (r.lifetime()) {
            case SINGLETON -> singleton(r);
            case SCOPED -> {
                if (scope == null) throw scopedOutsideScope(type);
                yield scope.scoped(type, () -> create(r, scope));
            }
            case TRANSIENT -> create(r, scope);
        };
        return type.cast(instance);
    }

    private Object singleton(Registration r) {
        synchronized (singletons) {
            Object existing = singletons.get(r.type());
            if (existing != null) return existing;
            Object created = create(r, null); // залежності singleton — лише з кореня, без області запиту
            singletons.put(r.type(), created);
            return created;
        }
    }

    private Object create(Registration r, ServiceScope scope) {
        Deque<Class<?>> chain = resolving.get();
        chain.addLast(r.type());
        try {
            Object instance = r.factory().create(scope == null ? this : scope);
            if (instance == null) throw new DependencyResolutionException("Фабрика повернула null для " + r.type().getSimpleName());
            return instance;
        } finally {
            chain.removeLast();
        }
    }

    private DependencyResolutionException scopedOutsideScope(Class<?> type) {
        Class<?> requester = resolving.get().peekLast();
        if (requester == null)
            return new DependencyResolutionException("Scoped-залежність " + type.getSimpleName()
                    + " можна отримати лише в межах області (scope) запиту");
        return new DependencyResolutionException(registrations.get(requester).lifetime() + " " + requester.getSimpleName()
                + " не може залежати від SCOPED " + type.getSimpleName() + " (" + chainTo(type) + ")");
    }

    private String chainTo(Class<?> type) {
        return Stream.concat(resolving.get().stream(), Stream.of(type))
                .map(Class::getSimpleName).collect(Collectors.joining(" -> "));
    }
}
