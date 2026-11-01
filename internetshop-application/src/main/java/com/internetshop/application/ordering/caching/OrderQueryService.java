package com.internetshop.application.ordering.caching;

import com.internetshop.application.ordering.OrderMapper;
import com.internetshop.application.ordering.OrderRepository;
import com.internetshop.application.ordering.dto.OrderDetailsDto;
import com.internetshop.application.shared.caching.DistributedCache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ПР9: сервіс читання замовлення з кешем — аналог OrderQueryService з методички (шаблон cache-aside):
 * <ol>
 *     <li>шукаємо в кеші за ключем {@code order:{id}} — якщо є, повертаємо без звернення до сховища;</li>
 *     <li>якщо немає (промах, TTL минув, кеш недоступний) — fallback: читаємо агрегат через OrderRepository;</li>
 *     <li>перетворюємо на DTO і кладемо в кеш на {@link #TIME_TO_LIVE} (60 с, як у методичці).</li>
 * </ol>
 * Відсутнє замовлення не кешується: інакше щойно створене замовлення з тим самим Id «не існувало б» до кінця TTL.
 * <p>
 * Кешується лише читання одного замовлення: це найчастіший запит (перегляд кошика) і його результат залежить
 * тільки від агрегату Order, тому всі зміни, що робять його застарілим, відомі — addItem і place.
 * Після них викликається {@link #evict(UUID)} (явна інвалідація); TTL обмежує застарілість, якщо інвалідація
 * не дійшла до кешу. Сервіс залежить лише від абстракцій і не знає, що за ним Memcached. Scoped, як і репозиторій.
 */
public final class OrderQueryService {

    /** Час життя запису: компроміс між актуальністю (зміни все одно інвалідуються) і кількістю звернень до сховища. */
    public static final Duration TIME_TO_LIVE = Duration.ofSeconds(60);

    private final DistributedCache cache;
    private final OrderRepository orders;
    private final OrderMapper mapper;

    public OrderQueryService(DistributedCache cache, OrderRepository orders, OrderMapper mapper) {
        this.cache = Objects.requireNonNull(cache);
        this.orders = Objects.requireNonNull(orders);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public static String cacheKey(UUID orderId) {
        return "order:" + Objects.requireNonNull(orderId);
    }

    public Optional<OrderDetailsDto> getById(UUID id) {
        if (id == null) return Optional.empty();
        String key = cacheKey(id);

        Optional<OrderDetailsDto> cached = cache.get(key).flatMap(OrderDetailsCacheFormat::read);
        if (cached.isPresent()) return cached;

        Optional<OrderDetailsDto> dto = orders.findById(id).map(mapper::toDetails);
        dto.ifPresent(d -> cache.set(key, OrderDetailsCacheFormat.write(d), TIME_TO_LIVE));
        return dto;
    }

    /** Інвалідація після зміни замовлення: наступне читання візьме актуальні дані зі сховища. */
    public void evict(UUID id) {
        cache.remove(cacheKey(id));
    }
}
