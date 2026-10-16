package com.internetshop.infrastructure.time;

import com.internetshop.application.shared.TimeProvider;

import java.time.Instant;

/** Реалізація TimeProvider на основі системного годинника (ПР3, DIP). Підключається в точці складання. */
public final class SystemTimeProvider implements TimeProvider {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
