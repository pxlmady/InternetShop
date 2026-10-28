package com.internetshop.webapi.di;

/**
 * Життєвий цикл залежності в контейнері (аналог ServiceLifetime в ASP.NET Core).
 */
public enum Lifetime {
    /** Один екземпляр на весь час роботи застосунку (AddSingleton). */
    SINGLETON,
    /** Один екземпляр на область (scope) — у вебзастосунку це один HTTP-запит (AddScoped). */
    SCOPED,
    /** Новий екземпляр при кожному зверненні до контейнера (AddTransient). */
    TRANSIENT
}
