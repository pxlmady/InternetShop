package com.internetshop.application.catalog;

import java.util.List;
import java.util.UUID;

/**
 * Вузький контракт «лише читання каталогу» (ПР3, ISP + DIP). Його реалізує CatalogService.
 * Клієнти, яким потрібно тільки знайти товар (адаптер Ordering → Catalog), залежать від цієї
 * абстракції, а не від усього CatalogService разом зі створенням і видаленням товарів.
 */
public interface ProductQueries {

    List<ProductDto> list();

    /** @throws ProductNotFoundException якщо товару немає */
    ProductDto get(UUID id);
}
