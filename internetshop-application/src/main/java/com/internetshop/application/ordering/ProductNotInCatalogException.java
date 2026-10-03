package com.internetshop.application.ordering;

import com.internetshop.application.shared.NotFoundException;

/** Товар, який намагаються додати до замовлення, відсутній у каталозі. */
public final class ProductNotInCatalogException extends NotFoundException {
    public ProductNotInCatalogException() {
        super("Товар не знайдено в каталозі.");
    }
}
