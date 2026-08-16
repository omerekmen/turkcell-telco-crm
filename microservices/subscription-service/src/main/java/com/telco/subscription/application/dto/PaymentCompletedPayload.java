package com.telco.subscription.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * JSON payload shape for the {@code payment.completed.v1} event consumed from Kafka. Fields mirror the
 * payment-service outbox payload. Unknown fields are ignored for forward-compatibility (ADR-019).
 *
 * <p>{@code orderId} is the saga correlation key used to fetch the order (the single synchronous hop)
 * and to key the compensation event on failure. {@code customerId} is present on the payload but the
 * activation path uses the order-service's customerId as authoritative.
 *
 * <p>{@code invoiceId} is nullable: populated only when the payment settles a specific invoice
 * (payment-service's direct/admin invoice-payment path, Section 14.2), never for an order-driven
 * onboarding charge. Its presence is the discriminator the consumer uses to skip the onboarding saga
 * entirely for invoice-settlement payments (see {@link com.telco.subscription.application.consumer.PaymentCompletedEventConsumer}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedPayload(
        String paymentId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String invoiceId,
        String occurredAt
) {
}
