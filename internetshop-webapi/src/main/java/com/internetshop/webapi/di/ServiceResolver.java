package com.internetshop.webapi.di;

/**
 * Те, що бачить фабрика під час створення залежності: можливість отримати інші зареєстровані залежності.
 * Цей інтерфейс використовують лише точка складання (InternetShopApp) і Router; у бізнес-логіку
 * (Domain, Application, Infrastructure, ендпоінти) контейнер не передається.
 */
public interface ServiceResolver {

    /** @throws DependencyResolutionException якщо тип не зареєстровано або порушено правила життєвого циклу */
    <T> T get(Class<T> type);
}
