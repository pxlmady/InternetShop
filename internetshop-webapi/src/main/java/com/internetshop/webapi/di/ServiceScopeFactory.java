package com.internetshop.webapi.di;

/**
 * Створення області (scope) для одного сценарію виконання, у вебзастосунку — для одного HTTP-запиту
 * (аналог IServiceScopeFactory). Від цієї абстракції залежить Router.
 */
public interface ServiceScopeFactory {

    ServiceScope createScope();
}
