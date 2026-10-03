package com.internetshop.application.catalog;

import com.internetshop.application.shared.NotFoundException;

public final class ProductNotFoundException extends NotFoundException {
    public ProductNotFoundException() {
        super("Товар не знайдено.");
    }
}
