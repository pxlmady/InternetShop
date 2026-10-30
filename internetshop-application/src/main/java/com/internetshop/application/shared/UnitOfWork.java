package com.internetshop.application.shared;

/**
 * ПР5 (Unit of Work): одна бізнес-операція = одне збереження.
 * Репозиторії лише накопичують зміни, а saveChanges() записує їх усі разом: або всі, або жодної.
 * Якщо збереження не вдалося (наприклад, ConcurrentChangeException), накопичені зміни відкидаються —
 * у сховищі залишається стан до початку операції.
 * <p>
 * Інтерфейс належить Application (як IUnitOfWork у методичці), реалізація — FileUnitOfWork в Infrastructure.
 * У контейнері — Scoped: один Unit of Work на HTTP-запит, спільний для всіх репозиторіїв цього запиту.
 */
public interface UnitOfWork {

    void saveChanges();
}
