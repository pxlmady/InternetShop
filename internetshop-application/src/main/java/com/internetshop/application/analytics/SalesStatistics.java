package com.internetshop.application.analytics;

import java.math.BigDecimal;

/**
 * Модель для читання «статистика продажів». Її оновлюють лише обробники подій,
 * тому OrderService про статистику нічого не знає. Зберігається в пам'яті (з нуля після перезапуску).
 */
public final class SalesStatistics {

    /** Знімок поточних показників для відповіді. */
    public record Snapshot(int ordersCreated, int ordersPlaced, int itemsSold, BigDecimal revenue) { }

    private int ordersCreated;
    private int ordersPlaced;
    private int itemsSold;
    private BigDecimal revenue = BigDecimal.ZERO.setScale(2);

    synchronized void orderCreated() {
        ordersCreated++;
    }

    synchronized void orderPlaced(int items, BigDecimal total) {
        ordersPlaced++;
        itemsSold += items;
        revenue = revenue.add(total);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(ordersCreated, ordersPlaced, itemsSold, revenue);
    }
}
