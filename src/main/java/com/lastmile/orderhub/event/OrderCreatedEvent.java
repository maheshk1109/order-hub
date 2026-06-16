package com.lastmile.orderhub.event;

/**
 * Event published to SQS when an order is created.
 * dispatch-engine consumes this event to assign a driver.
 *
 * WHY A SEPARATE EVENT CLASS?
 *   The Order entity has DB-specific fields (pk, sk, version, ttl).
 *   The event should only contain what consumers need.
 *   Decouples internal DB schema from inter-service contract.
 */
public record OrderCreatedEvent(
        String eventId,       // unique event ID — consumers use for idempotency
        String orderId,
        String customerId,
        String deliveryAddress,
        Double amount,
        String createdAt,
        String eventType      // ORDER_CREATED
) {}
