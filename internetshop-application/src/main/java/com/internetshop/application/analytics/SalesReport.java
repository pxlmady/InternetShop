package com.internetshop.application.analytics;

/**
 * Читання статистики продажів (ПР4, DI): HTTP-ендпоінт залежить від цієї абстракції, а не від
 * конкретного SalesStatistics, і не може змінювати показники — їх оновлюють лише обробники подій.
 */
public interface SalesReport {

    SalesStatistics.Snapshot snapshot();
}
