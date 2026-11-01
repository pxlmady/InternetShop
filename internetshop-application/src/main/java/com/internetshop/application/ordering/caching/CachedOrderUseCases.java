package com.internetshop.application.ordering.caching;

import com.internetshop.application.ordering.OrderNotFoundException;
import com.internetshop.application.ordering.OrderUseCases;
import com.internetshop.application.ordering.dto.AddOrderItemRequest;
import com.internetshop.application.ordering.dto.CreateOrderRequest;
import com.internetshop.application.ordering.dto.CreatedOrderDto;
import com.internetshop.application.ordering.dto.OrderDetailsDto;
import com.internetshop.application.ordering.dto.PlacedOrderDto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ПР9: кешування як декоратор над сценаріями замовлень. Реалізує той самий контракт OrderUseCases,
 * тож ендпоінти нічого не помічають, а OrderService (бізнес-сценарії) лишається без жодного рядка про кеш —
 * логіку кешування відокремлено від бізнес-логіки, як радить методичка.
 * <ul>
 *     <li>get — читання через OrderQueryService (кеш → fallback до сховища);</li>
 *     <li>addItem, place — зміна виконується OrderService, і лише ПІСЛЯ успішного saveChanges запис у кеші
 *     видаляється (як RemoveAsync після SaveChangesAsync у методичці). Якщо зміна відхилена — кеш не чіпаємо;</li>
 *     <li>create — нове замовлення ще не в кеші, інвалідувати нічого; list — не кешується: список змінюється
 *     при кожному створенні замовлення, тож кеш для нього потребував би інвалідації майже на кожен запит.</li>
 * </ul>
 * Кешуються лише операції читання; операції зміни завжди працюють з основним сховищем.
 */
public final class CachedOrderUseCases implements OrderUseCases {

    private final OrderUseCases inner;
    private final OrderQueryService queries;

    public CachedOrderUseCases(OrderUseCases inner, OrderQueryService queries) {
        this.inner = Objects.requireNonNull(inner);
        this.queries = Objects.requireNonNull(queries);
    }

    @Override
    public List<OrderDetailsDto> list() {
        return inner.list();
    }

    @Override
    public OrderDetailsDto get(UUID id) {
        return queries.getById(id).orElseThrow(OrderNotFoundException::new);
    }

    @Override
    public CreatedOrderDto create(CreateOrderRequest request) {
        return inner.create(request);
    }

    @Override
    public OrderDetailsDto addItem(UUID orderId, AddOrderItemRequest request) {
        OrderDetailsDto result = inner.addItem(orderId, request);
        queries.evict(orderId);
        return result;
    }

    @Override
    public PlacedOrderDto place(UUID orderId) {
        PlacedOrderDto result = inner.place(orderId);
        queries.evict(orderId);
        return result;
    }
}
