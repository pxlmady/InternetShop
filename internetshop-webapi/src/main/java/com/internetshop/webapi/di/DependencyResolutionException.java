package com.internetshop.webapi.di;

/** Помилка налаштування контейнера: незареєстрований тип, цикл або неправильний життєвий цикл. */
public final class DependencyResolutionException extends RuntimeException {

    public DependencyResolutionException(String message) {
        super(message);
    }

    public DependencyResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
