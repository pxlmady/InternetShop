package com.internetshop.domain.shared;

/** Порушення бізнес-правила предметної області. WebApi перетворює його на HTTP 400. */
public final class DomainRuleException extends RuntimeException {
    public DomainRuleException(String message) {
        super(message);
    }
}
