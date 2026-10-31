package com.internetshop.application.catalog;

import com.internetshop.application.catalog.dto.CreateProductRequest;
import com.internetshop.application.catalog.dto.CreatedProductDto;
import com.internetshop.application.catalog.dto.ProductDto;
import com.internetshop.domain.catalog.Product;

/**
 * Перетворення між сутністю Product і DTO (ПР3, SRP): окремо від сценаріїв CatalogService.
 * ПР4 (DI): абстракція замість статичного класу; CatalogService отримує її через конструктор.
 * ПР6 (DTO + мапінг): мапінг в обидва боки — вхідний DTO → нова сутність, сутність → DTO результату.
 */
public interface ProductMapper {

    /** Вхідний DTO → новий товар. Правила (назва, ціна) перевіряють ProductName, Money і Product. */
    Product toNewProduct(CreateProductRequest request);

    /** Перегляд каталогу: список і один товар. */
    ProductDto toDto(Product product);

    /** Результат додавання товару. */
    CreatedProductDto toCreated(Product product);
}
