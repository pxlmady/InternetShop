package com.internetshop.application.shared;

/** Запитаний об'єкт відсутній. WebApi перетворює на HTTP 404. */
public abstract class NotFoundException extends RuntimeException {
    protected NotFoundException(String message) {
        super(message);
    }
}
