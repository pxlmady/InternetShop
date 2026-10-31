package com.internetshop.application.catalog;

import com.internetshop.application.catalog.dto.CreateProductRequest;
import com.internetshop.application.catalog.dto.CreatedProductDto;

import java.util.UUID;

/**
 * Сценарії контексту Catalog як абстракція (ПР4, DI): HTTP-ендпоінти залежать від цього інтерфейсу,
 * а не від конкретного CatalogService. Читання успадковане від вузького ProductQueries (ПР3, ISP).
 * ПР6 (DTO): на вході — CreateProductRequest, на виході — DTO; доменних класів у сигнатурах немає.
 */
public interface CatalogUseCases extends ProductQueries {

    CreatedProductDto create(CreateProductRequest request);

    /** @throws ProductNotFoundException якщо товару немає */
    void delete(UUID id);
}
