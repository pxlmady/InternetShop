package com.internetshop.application.ordering.validation;

import com.internetshop.application.ordering.dto.CreateOrderRequest;
import com.internetshop.application.shared.validation.AbstractValidator;

import java.util.UUID;

/**
 * ПР8: валідатор команди «створити замовлення» (CreateOrderRequest — наш CreateOrderCommand з методички).
 * Синтаксична перевірка: покупця вказано і це не «порожній» UUID 00000000-0000-0000-0000-000000000000.
 * Виконується до use-case; інваріант CustomerId у Domain лишається останньою лінією захисту.
 */
public final class CreateOrderRequestValidator extends AbstractValidator<CreateOrderRequest> {

    private static final UUID EMPTY = new UUID(0, 0);

    public CreateOrderRequestValidator() {
        ruleFor("customerId", CreateOrderRequest::customerId)
                .notNull().withMessage("Потрібно вказати покупця (customerId).")
                .notEqual(EMPTY).withMessage("Ідентифікатор покупця не може бути порожнім (з самих нулів).");
    }
}
