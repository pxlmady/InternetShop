package com.internetshop.application.notifications;

import java.time.Instant;
import java.util.UUID;

/** Сповіщення покупцю (у цій роботі не надсилається назовні, а лише накопичується для перегляду). */
public record Notification(UUID customerId, UUID orderId, String message, Instant createdAt) {
}
