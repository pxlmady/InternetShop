package com.internetshop.infrastructure.persistence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ПР5: контекст даних — аналог AppDbContext з методички, але над текстовими файлами products.db і orders.db.
 * Тримає збережений стан («таблиці» записів) і вміє одним викликом commit застосувати набір змін атомарно.
 * <p>
 * Зберігає не доменні об'єкти, а записи (ProductRecord, OrderRecord) — модель збереження відокремлена
 * від доменної; перетворення виконують репозиторії. Бізнес-логіки тут немає.
 * Єдиний на застосунок (Singleton): два екземпляри над тими самими файлами розійшлися б у даних.
 * Інші шари про цей клас не знають — лише репозиторії та Unit of Work з цього пакета.
 */
public final class FileDataContext {

    record ProductRecord(UUID id, String name, String description, String price) { }

    record LineRecord(UUID id, UUID productId, String productName, String unitPrice, int quantity) { }

    record OrderRecord(UUID id, UUID customerId, String status, int version, List<LineRecord> lines) { }

    /** Одна зареєстрована зміна; застосовується до робочої копії таблиць під час commit. */
    interface Change {
        void applyTo(Tables tables);
    }

    /** Таблиці записів. commit змінює копію, тому невдала операція не залишає «половини» змін. */
    static final class Tables {
        final Map<UUID, ProductRecord> products;
        final Map<UUID, OrderRecord> orders;

        private Tables(Map<UUID, ProductRecord> products, Map<UUID, OrderRecord> orders) {
            this.products = new LinkedHashMap<>(products);
            this.orders = new LinkedHashMap<>(orders);
        }

        private Tables copy() {
            return new Tables(products, orders);
        }
    }

    private final TextDataFile productsFile;
    private final TextDataFile ordersFile;
    private Tables committed;

    public FileDataContext(Path dataDirectory) {
        this.productsFile = new TextDataFile(dataDirectory.resolve("products.db"), "# internetshop products v1");
        this.ordersFile = new TextDataFile(dataDirectory.resolve("orders.db"), "# internetshop orders v1");
        this.committed = new Tables(loadProducts(), loadOrders());
    }

    synchronized List<ProductRecord> products() {
        return List.copyOf(committed.products.values());
    }

    synchronized Optional<ProductRecord> product(UUID id) {
        return Optional.ofNullable(committed.products.get(id));
    }

    synchronized List<OrderRecord> orders() {
        return List.copyOf(committed.orders.values());
    }

    synchronized Optional<OrderRecord> order(UUID id) {
        return Optional.ofNullable(committed.orders.get(id));
    }

    /**
     * Застосовує всі зміни однієї бізнес-операції як одне ціле (викликає FileUnitOfWork.saveChanges).
     * 1) зміни застосовуються до копії таблиць — якщо будь-яка не проходить перевірку (версія, дубль Id),
     *    виняток, і збережений стан не змінено;
     * 2) змінені файли записуються (кожен — атомарно, через тимчасовий файл);
     * 3) лише після успішного запису копія стає поточним станом. Якщо запис другого файлу не вдався,
     *    уже записаний перший повертається до попереднього вмісту.
     */
    synchronized void commit(List<Change> changes) {
        if (changes.isEmpty()) return;
        Tables working = committed.copy();
        for (Change change : changes) change.applyTo(working);

        boolean productsChanged = !working.products.equals(committed.products);
        boolean ordersChanged = !working.orders.equals(committed.orders);
        boolean productsWritten = false;
        try {
            if (productsChanged) {
                writeProducts(working);
                productsWritten = true;
            }
            if (ordersChanged) writeOrders(working);
        } catch (RuntimeException e) {
            if (productsWritten) {
                try {
                    writeProducts(committed);
                } catch (RuntimeException restoreFailure) {
                    e.addSuppressed(restoreFailure);
                }
            }
            throw e;
        }
        committed = working;
    }

    private Map<UUID, ProductRecord> loadProducts() {
        Map<UUID, ProductRecord> records = new LinkedHashMap<>();
        for (String line : productsFile.readRecords()) {
            String[] f = TextDataFile.split(line, 4);
            ProductRecord r = new ProductRecord(UUID.fromString(f[0]), TextDataFile.decode(f[1]),
                    TextDataFile.decode(f[2]), f[3]);
            records.put(r.id(), r);
        }
        return records;
    }

    private Map<UUID, OrderRecord> loadOrders() {
        Map<UUID, OrderRecord> heads = new LinkedHashMap<>();
        Map<UUID, List<LineRecord>> lines = new LinkedHashMap<>();
        for (String line : ordersFile.readRecords()) {
            String[] head = line.split("\\" + TextDataFile.SEPARATOR, 2);
            if (head[0].equals("O")) {
                String[] f = TextDataFile.split(line, 5);
                UUID id = UUID.fromString(f[1]);
                heads.put(id, new OrderRecord(id, UUID.fromString(f[2]), f[3], Integer.parseInt(f[4]), List.of()));
                lines.put(id, new ArrayList<>());
            } else if (head[0].equals("L")) {
                String[] f = TextDataFile.split(line, 7);
                List<LineRecord> owner = lines.get(UUID.fromString(f[1]));
                if (owner == null) throw new IllegalStateException("Рядок замовлення без замовлення у файлі даних.");
                owner.add(new LineRecord(UUID.fromString(f[2]), UUID.fromString(f[3]),
                        TextDataFile.decode(f[4]), f[5], Integer.parseInt(f[6])));
            } else {
                throw new IllegalStateException("Пошкоджений запис у файлі даних.");
            }
        }
        Map<UUID, OrderRecord> records = new LinkedHashMap<>();
        heads.forEach((id, o) -> records.put(id,
                new OrderRecord(id, o.customerId(), o.status(), o.version(), List.copyOf(lines.get(id)))));
        return records;
    }

    private void writeProducts(Tables tables) {
        List<String> out = new ArrayList<>();
        for (ProductRecord r : tables.products.values()) {
            out.add(String.join(TextDataFile.SEPARATOR, r.id().toString(), TextDataFile.encode(r.name()),
                    TextDataFile.encode(r.description()), r.price()));
        }
        productsFile.writeRecords(out);
    }

    private void writeOrders(Tables tables) {
        List<String> out = new ArrayList<>();
        String s = TextDataFile.SEPARATOR;
        for (OrderRecord o : tables.orders.values()) {
            out.add(String.join(s, "O", o.id().toString(), o.customerId().toString(), o.status(),
                    Integer.toString(o.version())));
            for (LineRecord l : o.lines()) {
                out.add(String.join(s, "L", o.id().toString(), l.id().toString(), l.productId().toString(),
                        TextDataFile.encode(l.productName()), l.unitPrice(), Integer.toString(l.quantity())));
            }
        }
        ordersFile.writeRecords(out);
    }
}
