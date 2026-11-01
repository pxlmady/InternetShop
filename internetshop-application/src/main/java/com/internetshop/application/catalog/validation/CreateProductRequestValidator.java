package com.internetshop.application.catalog.validation;

import com.internetshop.application.catalog.ProductRepository;
import com.internetshop.application.catalog.dto.CreateProductRequest;
import com.internetshop.application.shared.validation.AbstractValidator;
import com.internetshop.domain.catalog.Product;
import com.internetshop.domain.catalog.ProductName;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

/**
 * ПР8: валідатор запиту «додати товар до каталогу».
 * <ul>
 *   <li>синтаксична валідація: назва непорожня й не задовга, опис не задовгий, ціна вказана, додатна,
 *       не більша за 1 000 000 і має не більше двох знаків після коми;</li>
 *   <li>семантична валідація з доступом до даних: назва товару унікальна в каталозі. Для цього валідатор
 *       отримує ProductRepository через конструктор — так само, як у C# валідатор отримав би DbContext.</li>
 * </ul>
 * Межі довжини беруться з доменної моделі (ProductName.MAX_LENGTH, Product.MAX_DESCRIPTION_LENGTH), щоб правила
 * Application і Domain не розійшлися; сам Domain свої інваріанти перевіряє й надалі.
 */
public final class CreateProductRequestValidator extends AbstractValidator<CreateProductRequest> {

    static final BigDecimal MAX_PRICE = new BigDecimal("1000000");

    public CreateProductRequestValidator(ProductRepository products) {
        Objects.requireNonNull(products);

        ruleFor("name", CreateProductRequest::name)
                .notEmpty().withMessage("Назва товару обов'язкова.")
                .maximumLength(ProductName.MAX_LENGTH).withMessage("Назва товару має містити не більше " + ProductName.MAX_LENGTH + " символів.")
                .must(name -> isUnique(products, name)).withMessage("Товар з такою назвою вже є в каталозі.");

        ruleFor("description", CreateProductRequest::description)
                .maximumLength(Product.MAX_DESCRIPTION_LENGTH)
                .withMessage("Опис товару має містити не більше " + Product.MAX_DESCRIPTION_LENGTH + " символів.");

        ruleFor("price", CreateProductRequest::price)
                .notNull().withMessage("Потрібно вказати ціну товару.")
                .greaterThan(BigDecimal.ZERO).withMessage("Ціна товару має бути більшою за нуль.")
                .lessThanOrEqualTo(MAX_PRICE).withMessage("Ціна товару не може перевищувати 1 000 000.")
                .must(price -> price.stripTrailingZeros().scale() <= 2).withMessage("Ціна може містити не більше двох знаків після коми.");
    }

    /** Порівнюємо так, як назву нормалізує Domain (без пробілів по краях), і без урахування регістру. */
    private static boolean isUnique(ProductRepository products, String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return products.findAll().stream()
                .noneMatch(p -> p.getName().value().toLowerCase(Locale.ROOT).equals(normalized));
    }
}
