package com.internetshop.application.ordering;

import com.internetshop.application.shared.NotFoundException;

public final class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException() {
        super("Замовлення не знайдено.");
    }
}
