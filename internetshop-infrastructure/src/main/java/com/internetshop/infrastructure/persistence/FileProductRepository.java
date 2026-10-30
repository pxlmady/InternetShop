package com.internetshop.infrastructure.persistence;

import com.internetshop.application.catalog.ProductRepository;
import com.internetshop.domain.catalog.Product;
import com.internetshop.domain.catalog.ProductName;
import com.internetshop.domain.shared.Money;
import com.internetshop.infrastructure.persistence.FileDataContext.ProductRecord;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ПР5: реалізація ProductRepository в Infrastructure. Перетворює Product ↔ ProductRecord
 * і реєструє зміни в Unit of Work; записує їх FileUnitOfWork.saveChanges.
 */
public final class FileProductRepository implements ProductRepository {

    private final FileDataContext context;
    private final FileUnitOfWork unitOfWork;

    public FileProductRepository(FileDataContext context, FileUnitOfWork unitOfWork) {
        this.context = Objects.requireNonNull(context);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
    }

    @Override
    public List<Product> findAll() {
        return context.products().stream().map(FileProductRepository::toDomain).toList();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return context.product(id).map(FileProductRepository::toDomain);
    }

    @Override
    public void add(Product product) {
        ProductRecord record = new ProductRecord(product.getId(), product.getName().value(),
                product.getDescription(), product.getPrice().toString());
        unitOfWork.register(tables -> {
            if (tables.products.containsKey(record.id()))
                throw new IllegalStateException("Товар з таким Id уже збережено.");
            tables.products.put(record.id(), record);
        });
    }

    @Override
    public void remove(UUID id) {
        unitOfWork.register(tables -> tables.products.remove(id));
    }

    private static Product toDomain(ProductRecord r) {
        return Product.restore(r.id(), new ProductName(r.name()), r.description(), Money.of(r.price()));
    }
}
