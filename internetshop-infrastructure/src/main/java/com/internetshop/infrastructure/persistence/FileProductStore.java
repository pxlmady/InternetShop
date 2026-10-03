package com.internetshop.infrastructure.persistence;

import com.internetshop.application.catalog.ProductStore;
import com.internetshop.domain.catalog.Product;
import com.internetshop.domain.catalog.ProductName;
import com.internetshop.domain.shared.Money;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Реалізація ProductStore у файлі products.db.
 * Зберігає не доменні об'єкти, а «рядки» (ProductRecord) — модель збереження відокремлена від доменної.
 */
public final class FileProductStore implements ProductStore {

    private record ProductRecord(UUID id, String name, String description, String price) { }

    private final TextDataFile file;
    private final Map<UUID, ProductRecord> records = new LinkedHashMap<>();

    public FileProductStore(Path dataDirectory) {
        this.file = new TextDataFile(dataDirectory.resolve("products.db"), "# internetshop products v1");
        for (String line : file.readRecords()) {
            String[] f = TextDataFile.split(line, 4);
            ProductRecord r = new ProductRecord(UUID.fromString(f[0]), TextDataFile.decode(f[1]),
                    TextDataFile.decode(f[2]), f[3]);
            records.put(r.id(), r);
        }
    }

    @Override
    public synchronized List<Product> findAll() {
        return records.values().stream().map(FileProductStore::toDomain).toList();
    }

    @Override
    public synchronized Optional<Product> findById(UUID id) {
        return Optional.ofNullable(records.get(id)).map(FileProductStore::toDomain);
    }

    @Override
    public synchronized void add(Product product) {
        if (records.containsKey(product.getId()))
            throw new IllegalStateException("Товар з таким Id уже збережено.");
        ProductRecord r = new ProductRecord(product.getId(), product.getName().value(),
                product.getDescription(), product.getPrice().toString());
        records.put(r.id(), r);
        try {
            persist();
        } catch (RuntimeException e) {
            records.remove(r.id()); // пам'ять і файл мають залишатися узгодженими
            throw e;
        }
    }

    @Override
    public synchronized void remove(UUID id) {
        ProductRecord removed = records.remove(id);
        if (removed == null) return;
        try {
            persist();
        } catch (RuntimeException e) {
            records.put(id, removed); // відновлюємо, якщо файл не вдалося записати
            throw e;
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (ProductRecord r : records.values()) {
            lines.add(String.join(TextDataFile.SEPARATOR, r.id().toString(), TextDataFile.encode(r.name()),
                    TextDataFile.encode(r.description()), r.price()));
        }
        file.writeRecords(lines);
    }

    private static Product toDomain(ProductRecord r) {
        return Product.restore(r.id(), new ProductName(r.name()), r.description(), Money.of(r.price()));
    }
}
