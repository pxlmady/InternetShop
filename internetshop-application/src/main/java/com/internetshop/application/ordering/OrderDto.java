package com.internetshop.application.ordering;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Дані замовлення для відповіді без доменної поведінки. */
public record OrderDto(UUID id, UUID customerId, String status, List<OrderLineDto> lines, BigDecimal total) {
}
