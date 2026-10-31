package com.internetshop.application.catalog;

import com.internetshop.application.catalog.dto.CreateProductRequest;
import com.internetshop.application.catalog.dto.CreatedProductDto;
import com.internetshop.application.catalog.dto.ProductDto;
import com.internetshop.domain.catalog.Product;
import com.internetshop.domain.catalog.ProductName;
import com.internetshop.domain.shared.DomainRuleException;
import com.internetshop.domain.shared.Money;

import java.util.Objects;

/** Ручна реалізація ProductMapper (ПР6) без стану: у контейнері — Transient (ПР4). */
public final class ProductDtoMapper implements ProductMapper {

    @Override
    public Product toNewProduct(CreateProductRequest request) {
        Objects.requireNonNull(request);
        if (request.price() == null) throw new DomainRuleException("Потрібно вказати ціну товару.");
        return new Product(new ProductName(request.name()), request.description(), new Money(request.price()));
    }

    @Override
    public ProductDto toDto(Product p) {
        return new ProductDto(p.getId(), p.getName().value(), p.getDescription(), p.getPrice().amount());
    }

    @Override
    public CreatedProductDto toCreated(Product p) {
        return new CreatedProductDto(p.getId(), p.getName().value());
    }
}
