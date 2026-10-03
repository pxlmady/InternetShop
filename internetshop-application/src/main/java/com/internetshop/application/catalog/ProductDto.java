package com.internetshop.application.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Дані товару для відповіді без доменної поведінки.
 * Це також опублікована мова (Published Language) контексту Catalog для інших контекстів.
 */
public record ProductDto(UUID id, String name, String description, BigDecimal price) {
}
