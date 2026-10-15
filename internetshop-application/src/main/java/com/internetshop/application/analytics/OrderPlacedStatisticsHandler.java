package com.internetshop.application.analytics;

import com.internetshop.application.ordering.events.OrderPlaced;
import com.internetshop.application.shared.events.EventHandler;

import java.util.Objects;

/** Реакція «врахувати продаж у статистиці» на подію OrderPlaced. Незалежна від сповіщень. */
public final class OrderPlacedStatisticsHandler implements EventHandler<OrderPlaced> {

    private final SalesStatistics statistics;

    public OrderPlacedStatisticsHandler(SalesStatistics statistics) {
        this.statistics = Objects.requireNonNull(statistics);
    }

    @Override
    public void handle(OrderPlaced event) {
        statistics.orderPlaced(event.itemCount(), event.total());
    }
}
