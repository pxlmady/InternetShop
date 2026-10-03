package com.internetshop.domain.ordering;

import com.internetshop.domain.shared.DomainRuleException;

import java.util.UUID;

/**
 * Value Object: посилання на покупця. Профіль покупця належав би окремому контексту
 * Customer Management; у Ordering потрібен лише його ідентифікатор.
 */
public record CustomerId(UUID value) {

    private static final UUID EMPTY = new UUID(0, 0);

    public CustomerId {
        if (value == null || EMPTY.equals(value))
            throw new DomainRuleException("Потрібен непорожній ідентифікатор покупця.");
    }
}
