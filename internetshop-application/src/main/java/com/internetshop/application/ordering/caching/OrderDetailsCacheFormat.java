package com.internetshop.application.ordering.caching;

import com.internetshop.application.ordering.dto.OrderDetailsDto;
import com.internetshop.application.ordering.dto.OrderLineDto;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ПР9: перетворення OrderDetailsDto ↔ рядок для кешу — роль JsonSerializer.Serialize/Deserialize у методичці.
 * У кеш потрапляє лише DTO (без customerId і версії агрегату), а не доменний Order.
 * <p>
 * Формат простий і без сторонніх бібліотек: перший рядок — версія формату, далі замовлення та його рядки,
 * поля розділені табуляцією; назва товару кодується Base64, тож будь-які символи в ній не ламають формат.
 * Якщо запис у кеші пошкоджений або має іншу версію формату, read повертає empty — це просто «промах».
 */
final class OrderDetailsCacheFormat {

    private static final String VERSION = "order-details/v1";

    private OrderDetailsCacheFormat() { }

    static String write(OrderDetailsDto order) {
        StringBuilder sb = new StringBuilder(VERSION).append('\n')
                .append(order.id()).append('\t').append(order.status()).append('\t').append(order.total().toPlainString());
        for (OrderLineDto line : order.lines()) {
            sb.append('\n').append(line.productId())
                    .append('\t').append(Base64.getEncoder().encodeToString(line.productName().getBytes(StandardCharsets.UTF_8)))
                    .append('\t').append(line.unitPrice().toPlainString())
                    .append('\t').append(line.quantity())
                    .append('\t').append(line.lineTotal().toPlainString());
        }
        return sb.toString();
    }

    static Optional<OrderDetailsDto> read(String value) {
        try {
            String[] rows = value.split("\n", -1);
            if (rows.length < 2 || !rows[0].equals(VERSION)) return Optional.empty();
            String[] head = rows[1].split("\t", -1);
            if (head.length != 3) return Optional.empty();
            List<OrderLineDto> lines = new ArrayList<>();
            for (int i = 2; i < rows.length; i++) {
                String[] f = rows[i].split("\t", -1);
                if (f.length != 5) return Optional.empty();
                lines.add(new OrderLineDto(UUID.fromString(f[0]),
                        new String(Base64.getDecoder().decode(f[1]), StandardCharsets.UTF_8),
                        new BigDecimal(f[2]), Integer.parseInt(f[3]), new BigDecimal(f[4])));
            }
            return Optional.of(new OrderDetailsDto(UUID.fromString(head[0]), head[1], List.copyOf(lines), new BigDecimal(head[2])));
        } catch (IllegalArgumentException e) { // NumberFormatException теж сюди
            return Optional.empty();
        }
    }
}
