package com.internetshop.webapi.http;

/** Помилка формату HTTP-запиту (не бізнес-правило): некоректний JSON, Id, тип поля тощо. */
public final class BadRequestException extends RuntimeException {
    private final int status;

    public BadRequestException(String message) {
        this(400, message);
    }

    public BadRequestException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
