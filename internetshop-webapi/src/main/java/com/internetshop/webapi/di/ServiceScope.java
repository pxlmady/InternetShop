package com.internetshop.webapi.di;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Область життя scoped-залежностей. Router створює одну область на кожен HTTP-запит, тому в межах
 * запиту всі компоненти отримують ті самі екземпляри scoped-залежностей, а різні запити — різні.
 * Після close() область закривається: її scoped-екземпляри, що реалізують AutoCloseable, закриваються,
 * а подальші звернення до області заборонені.
 */
public final class ServiceScope implements ServiceResolver, AutoCloseable {

    private final ServiceProvider root;
    private final Map<Class<?>, Object> instances = new LinkedHashMap<>();
    private boolean closed;

    ServiceScope(ServiceProvider root) {
        this.root = root;
    }

    @Override
    public <T> T get(Class<T> type) {
        synchronized (this) {
            if (closed) throw new DependencyResolutionException("Область (scope) уже закрита");
        }
        return root.resolve(type, this);
    }

    synchronized Object scoped(Class<?> type, Supplier<Object> create) {
        if (closed) throw new DependencyResolutionException("Область (scope) уже закрита");
        Object existing = instances.get(type);
        if (existing != null) return existing;
        Object created = create.get();
        instances.put(type, created);
        return created;
    }

    @Override
    public void close() {
        List<Object> created;
        synchronized (this) {
            if (closed) return;
            closed = true;
            created = new ArrayList<>(instances.values());
            instances.clear();
        }
        // Закриваємо у зворотному порядку створення: спершу ті, що залежать від інших.
        // Один екземпляр може бути зареєстрований під кількома інтерфейсами — закриваємо його один раз.
        Set<Object> done = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = created.size() - 1; i >= 0; i--) {
            if (created.get(i) instanceof AutoCloseable closeable && done.add(closeable)) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    throw new DependencyResolutionException("Помилка закриття " + closeable.getClass().getSimpleName(), e);
                }
            }
        }
    }
}
