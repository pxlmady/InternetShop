package com.internetshop.infrastructure.persistence;

import com.internetshop.application.shared.UnitOfWork;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ПР5: реалізація Unit of Work (аналог UnitOfWork над AppDbContext у методичці).
 * Репозиторії цього запиту реєструють тут свої зміни; saveChanges передає їх у FileDataContext одним
 * атомарним commit. Після saveChanges (успішного чи ні) список змін очищується.
 * <p>
 * Scoped: один на HTTP-запит. Під час закриття області запиту незбережені зміни відкидаються (відкат).
 */
public final class FileUnitOfWork implements UnitOfWork, AutoCloseable {

    private final FileDataContext context;
    private final List<FileDataContext.Change> pending = new ArrayList<>();

    public FileUnitOfWork(FileDataContext context) {
        this.context = Objects.requireNonNull(context);
    }

    /** Викликають репозиторії: зміна запам'ятовується, але ще не записується. */
    void register(FileDataContext.Change change) {
        pending.add(change);
    }

    @Override
    public void saveChanges() {
        try {
            context.commit(List.copyOf(pending));
        } finally {
            pending.clear(); // у разі помилки зміни відкинуто — у сховищі залишився попередній стан
        }
    }

    @Override
    public void close() {
        pending.clear();
    }
}
