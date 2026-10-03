package com.internetshop.domain.catalog;

import com.internetshop.domain.shared.DomainRuleException;

/** Value Object: перевірена назва товару (1–120 символів після обрізання пробілів). */
public record ProductName(String value) {

    public static final int MAX_LENGTH = 120;

    public ProductName {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH)
            throw new DomainRuleException("Назва товару має містити від 1 до " + MAX_LENGTH + " символів.");
        value = normalized;
    }

    @Override
    public String toString() {
        return value;
    }
}
