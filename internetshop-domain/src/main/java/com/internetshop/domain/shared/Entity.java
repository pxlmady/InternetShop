package com.internetshop.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Базова сутність: має ідентичність (Id), яка не змінюється протягом життя об'єкта.
 * Дві сутності рівні, якщо в них однаковий Id, навіть якщо інші поля відрізняються.
 * Базовий клас не є вимогою DDD — він лише прибирає дублювання Id.
 */
public abstract class Entity {
    private final UUID id;

    /** Нова сутність: Id генерується в домені, а не в базі даних. */
    protected Entity() {
        this(UUID.randomUUID());
    }

    /** Відновлення сутності зі сховища з уже відомим Id. */
    protected Entity(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public UUID getId() {
        return id;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        return id.equals(((Entity) other).id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
