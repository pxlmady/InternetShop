package com.internetshop.application.ordering.validation;

import com.internetshop.application.ordering.ProductCatalog;
import com.internetshop.application.ordering.dto.AddOrderItemRequest;
import com.internetshop.application.shared.validation.AbstractValidator;

import java.util.Objects;

/**
 * ПР8: валідатор запиту «додати товар до замовлення».
 * <ul>
 *   <li>синтаксична: товар вказано, кількість — додатна;</li>
 *   <li>семантична з доступом до даних: товар існує в каталозі. Валідатор отримує контракт ProductCatalog
 *       через конструктор (DI, Scoped), тому сам не знає, звідки беруться дані.</li>
 * </ul>
 * Верхню межу кількості (не більше 99 одиниць одного товару в замовленні) свідомо не дублюємо: це інваріант
 * агрегата Order, який залежить від того, що вже лежить у замовленні, і перевіряє його Domain.
 */
public final class AddOrderItemRequestValidator extends AbstractValidator<AddOrderItemRequest> {

    public AddOrderItemRequestValidator(ProductCatalog catalog) {
        Objects.requireNonNull(catalog);

        ruleFor("productId", AddOrderItemRequest::productId)
                .notNull().withMessage("Потрібно вказати товар (productId).")
                .must(id -> catalog.findProduct(id).isPresent()).withMessage("Товару з таким ідентифікатором немає в каталозі.");

        ruleFor("quantity", AddOrderItemRequest::quantity)
                .greaterThan(0).withMessage("Кількість має бути додатним цілим числом.");
    }
}
