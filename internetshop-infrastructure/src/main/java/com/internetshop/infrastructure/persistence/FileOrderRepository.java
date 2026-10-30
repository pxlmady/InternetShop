package com.internetshop.infrastructure.persistence;

import com.internetshop.application.ordering.OrderRepository;
import com.internetshop.application.shared.ConcurrentChangeException;
import com.internetshop.domain.ordering.CustomerId;
import com.internetshop.domain.ordering.Order;
import com.internetshop.domain.ordering.OrderLine;
import com.internetshop.domain.ordering.OrderStatus;
import com.internetshop.domain.ordering.Quantity;
import com.internetshop.domain.shared.Money;
import com.internetshop.infrastructure.persistence.FileDataContext.LineRecord;
import com.internetshop.infrastructure.persistence.FileDataContext.OrderRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ПР5: реалізація OrderRepository в Infrastructure. Лише перетворює агрегат Order ↔ запис OrderRecord,
 * читає з FileDataContext і реєструє зміни в Unit of Work — бізнес-правил тут немає (їх перевіряє Order).
 * Сам у файл не пише: запис виконує FileUnitOfWork.saveChanges.
 * <p>
 * Оптимістичне блокування: зміна приймається лише тоді, коли збережена версія збігається з версією,
 * яку було прочитано. Інакше під час saveChanges — ConcurrentChangeException (HTTP 409).
 */
public final class FileOrderRepository implements OrderRepository {

    private final FileDataContext context;
    private final FileUnitOfWork unitOfWork;

    public FileOrderRepository(FileDataContext context, FileUnitOfWork unitOfWork) {
        this.context = Objects.requireNonNull(context);
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
    }

    @Override
    public List<Order> findAll() {
        return context.orders().stream().map(FileOrderRepository::toDomain).toList();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        // Кожен виклик створює нову копію агрегату — як окреме читання з БД.
        return context.order(id).map(FileOrderRepository::toDomain);
    }

    @Override
    public void add(Order order) {
        OrderRecord record = toRecord(order, order.getVersion()); // знімок стану на момент виклику
        unitOfWork.register(tables -> {
            if (tables.orders.containsKey(record.id()))
                throw new IllegalStateException("Замовлення з таким Id уже збережено.");
            tables.orders.put(record.id(), record);
        });
    }

    @Override
    public void update(Order order) {
        int readVersion = order.getVersion();
        OrderRecord record = toRecord(order, readVersion + 1);
        unitOfWork.register(tables -> {
            OrderRecord stored = tables.orders.get(record.id());
            if (stored == null || stored.version() != readVersion) throw new ConcurrentChangeException();
            tables.orders.put(record.id(), record);
        });
    }

    private static OrderRecord toRecord(Order order, int version) {
        List<LineRecord> lines = new ArrayList<>();
        for (OrderLine l : order.getLines()) {
            lines.add(new LineRecord(l.getId(), l.getProductId(), l.getProductName(),
                    l.getUnitPrice().toString(), l.getQuantity().value()));
        }
        return new OrderRecord(order.getId(), order.getCustomerId().value(), order.getStatus().name(), version,
                List.copyOf(lines));
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
