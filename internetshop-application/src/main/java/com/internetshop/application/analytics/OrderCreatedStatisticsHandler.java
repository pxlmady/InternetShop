package com.internetshop.application.analytics;

import com.internetshop.application.ordering.events.OrderCreated;
import com.internetshop.application.shared.events.EventHandler;

import java.util.Objects;

/** Реакція «врахувати нове замовлення в статистиці» на подію OrderCreated. */
public final class OrderCreatedStatisticsHandler implements EventHandler<OrderCreated> {

    private final SalesStatistics statistics;

    public OrderCreatedStatisticsHandler(SalesStatistics statistics) {
        this.statistics = Objects.requireNonNull(statistics);
    }

    @Override
    public void handle(OrderCreated event) {
        statistics.orderCreated();
    }
}
