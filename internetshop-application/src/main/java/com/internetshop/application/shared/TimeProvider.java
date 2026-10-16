package com.internetshop.application.shared;

import java.time.Instant;

/**
 * Абстракція джерела поточного часу (ПР3, DIP). Сценарії Application отримують час через цей контракт,
 * а не викликають системний годинник напряму: реалізацію (SystemTimeProvider) вибирає точка складання,
 * а в перевірках її можна замінити на фіксований час.
 */
public interface TimeProvider {

    Instant now();
}
