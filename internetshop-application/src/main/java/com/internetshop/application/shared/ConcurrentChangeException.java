package com.internetshop.application.shared;

/** Об'єкт змінився після читання; операцію треба повторити з актуальними даними. WebApi → HTTP 409. */
public final class ConcurrentChangeException extends RuntimeException {
    public ConcurrentChangeException() {
        super("Дані одночасно змінилися. Оновіть їх та повторіть запит.");
    }
}
