package com.internetshop.application.analytics;

import java.math.BigDecimal;

/**
 * Модель для читання «статистика продажів». Її оновлюють лише обробники подій,
 * тому OrderService про статистику нічого не знає. Зберігається в пам'яті (з нуля після перезапуску).
 * ПР4: має спільний стан для всього застосунку, тому в контейнері — Singleton.
 */
public final class SalesStatistics implements SalesReport {

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

    @Override
    public synchronized Snapshot snapshot() {
        return new Snapshot(ordersCreated, ordersPlaced, itemsSold, revenue);
    }
}
