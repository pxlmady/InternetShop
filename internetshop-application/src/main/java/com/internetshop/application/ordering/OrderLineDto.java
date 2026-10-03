package com.internetshop.application.ordering;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineDto(UUID productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
}
