package com.internetshop.application.shared.caching;

import java.time.Duration;
import java.util.Optional;

/**
 * ПР9 (кешування): розподілений кеш з точки зору прикладного рівня — аналог IMemcachedClient у методичці
 * (GetAsync / SetAsync / RemoveAsync), але як власна абстракція Application. Реалізацію (MemcachedDistributedCache
 * в Infrastructure) вибирає лише точка складання, тож сценарії не знають ні про Memcached, ні про сокети.
 * <p>
 * Кеш — необов'язкове прискорення, а не джерело даних. Тому контракт такий: якщо сервіс кешування недоступний,
 * get повертає «промах», а set і remove нічого не роблять — сценарій продовжує роботу з основним сховищем.
 * Значення — рядки: як і в методичці, об'єкт перед записом серіалізується, після читання — десеріалізується.
 */
public interface DistributedCache {

    /** Значення за ключем або Optional.empty(), якщо його немає, термін життя минув чи кеш недоступний. */
    Optional<String> get(String key);

    /** Зберігає значення з обмеженим часом життя (TTL); після нього запис автоматично зникає. */
    void set(String key, String value, Duration timeToLive);

    /** Явна інвалідація: видаляє запис після зміни даних. */
    void remove(String key);
}
