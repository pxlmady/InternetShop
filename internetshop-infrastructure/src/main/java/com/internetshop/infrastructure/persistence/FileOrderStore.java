package com.internetshop.infrastructure.persistence;

import com.internetshop.application.ordering.OrderStore;
import com.internetshop.application.shared.ConcurrentChangeException;
import com.internetshop.domain.ordering.CustomerId;
import com.internetshop.domain.ordering.Order;
import com.internetshop.domain.ordering.OrderLine;
import com.internetshop.domain.ordering.OrderStatus;
import com.internetshop.domain.ordering.Quantity;
import com.internetshop.domain.shared.Money;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Реалізація OrderStore у файлі orders.db. Агрегат зберігається цілком: замовлення та його рядки.
 * Оптимістичне блокування: update приймається лише тоді, коли збережена версія збігається
 * з версією, яку було прочитано. Інакше — ConcurrentChangeException (HTTP 409).
 */
public final class FileOrderStore implements OrderStore {

    private record LineRecord(UUID id, UUID productId, String productName, String unitPrice, int quantity) { }

    private record OrderRecord(UUID id, UUID customerId, String status, int version, List<LineRecord> lines) { }

    private final TextDataFile file;
    private final Map<UUID, OrderRecord> records = new LinkedHashMap<>();

    public FileOrderStore(Path dataDirectory) {
        this.file = new TextDataFile(dataDirectory.resolve("orders.db"), "# internetshop orders v1");
        load();
    }

    @Override
    public synchronized List<Order> findAll() {
        return records.values().stream().map(FileOrderStore::toDomain).toList();
    }

    @Override
    public synchronized Optional<Order> findById(UUID id) {
        // Кожен виклик створює нову копію агрегату — як окреме читання з БД.
        return Optional.ofNullable(records.get(id)).map(FileOrderStore::toDomain);
    }

    @Override
    public synchronized void add(Order order) {
        if (records.containsKey(order.getId()))
            throw new IllegalStateException("Замовлення з таким Id уже збережено.");
        writeThrough(order.getId(), toRecord(order, order.getVersion()));
    }

    @Override
    public synchronized void update(Order order) {
        OrderRecord stored = records.get(order.getId());
        if (stored == null || stored.version() != order.getVersion())
            throw new ConcurrentChangeException();
        writeThrough(order.getId(), toRecord(order, stored.version() + 1));
    }

    private void writeThrough(UUID id, OrderRecord newRecord) {
        OrderRecord previous = records.put(id, newRecord);
        try {
            persist();
        } catch (RuntimeException e) {
            if (previous == null) records.remove(id); else records.put(id, previous);
            throw e;
        }
    }

    private void load() {
        for (String line : file.readRecords()) {
            String[] head = line.split("\\" + TextDataFile.SEPARATOR, 2);
            if (head[0].equals("O")) {
                String[] f = TextDataFile.split(line, 5);
                UUID id = UUID.fromString(f[1]);
                records.put(id, new OrderRecord(id, UUID.fromString(f[2]), f[3], Integer.parseInt(f[4]), new ArrayList<>()));
            } else if (head[0].equals("L")) {
                String[] f = TextDataFile.split(line, 7);
                OrderRecord owner = records.get(UUID.fromString(f[1]));
                if (owner == null) throw new IllegalStateException("Рядок замовлення без замовлення у файлі даних.");
                owner.lines().add(new LineRecord(UUID.fromString(f[2]), UUID.fromString(f[3]),
                        TextDataFile.decode(f[4]), f[5], Integer.parseInt(f[6])));
            } else {
                throw new IllegalStateException("Пошкоджений запис у файлі даних.");
            }
        }
    }

    private void persist() {
        List<String> out = new ArrayList<>();
        String s = TextDataFile.SEPARATOR;
        for (OrderRecord o : records.values()) {
            out.add(String.join(s, "O", o.id().toString(), o.customerId().toString(), o.status(),
                    Integer.toString(o.version())));
            for (LineRecord l : o.lines()) {
                out.add(String.join(s, "L", o.id().toString(), l.id().toString(), l.productId().toString(),
                        TextDataFile.encode(l.productName()), l.unitPrice(), Integer.toString(l.quantity())));
            }
        }
        file.writeRecords(out);
    }

    private static OrderRecord toRecord(Order order, int version) {
        List<LineRecord> lines = new ArrayList<>();
        for (OrderLine l : order.getLines()) {
            lines.add(new LineRecord(l.getId(), l.getProductId(), l.getProductName(),
                    l.getUnitPrice().toString(), l.getQuantity().value()));
        }
        return new OrderRecord(order.getId(), order.getCustomerId().value(), order.getStatus().name(), version, List.copyOf(lines));
    }

    private static Order toDomain(OrderRecord r) {
        List<OrderLine> lines = new ArrayList<>();
        for (LineRecord l : r.lines()) {
            lines.add(OrderLine.restore(l.id(), l.productId(), l.productName(), Money.of(l.unitPrice()),
                    new Quantity(l.quantity())));
        }
        return Order.restore(r.id(), new CustomerId(r.customerId()), OrderStatus.valueOf(r.status()), lines, r.version());
    }
}
