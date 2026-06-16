package com.lastmile.orderhub.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastmile.orderhub.model.Order;
import com.lastmile.orderhub.repository.OrderRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OUTBOX PATTERN — Guaranteed Message Delivery
 *
 * PROBLEM:
 *   Normal flow: save to DynamoDB → publish to SQS
 *   If app crashes AFTER DynamoDB save but BEFORE SQS publish:
 *     → Order saved in DB but dispatch-engine never notified
 *     → Order stuck in PENDING forever
 *
 * OUTBOX SOLUTION:
 *   1. Save order to DynamoDB with publishStatus=PENDING_PUBLISH (atomic write)
 *   2. Outbox relay runs every 10 seconds:
 *      a. Reads all PENDING_PUBLISH orders
 *      b. Publishes each to SQS
 *      c. Marks as PUBLISHED
 *   3. If app crashes before step 2c → relay picks it up on next run
 *   4. dispatch-engine must be idempotent (may receive duplicate events)
 *
 * WHY NOT JUST PUBLISH IN THE SAME TRANSACTION?
 *   DynamoDB + SQS are two different systems — no distributed transaction.
 *   You cannot atomically write to both.
 *   Outbox pattern solves this with eventual consistency.
 *
 * AT-LEAST-ONCE DELIVERY:
 *   Outbox guarantees at-least-once — consumer MUST handle duplicates.
 *   dispatch-engine uses eventId for idempotency dedup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OrderRepository orderRepository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.dispatch-queue}")
    private String dispatchQueue;

    @Scheduled(fixedDelay = 10_000) // runs every 10 seconds
    public void relay() {
        List<Order> pending = orderRepository.findPendingPublish();

        if (pending.isEmpty()) return;

        log.info("Outbox relay: found {} pending events", pending.size());

        for (Order order : pending) {
            try {
                // Publish to SQS
                String payload = objectMapper.writeValueAsString(buildEvent(order));
                sqsTemplate.send(dispatchQueue, payload);

                // Mark as PUBLISHED
                order.setPublishStatus("PUBLISHED");
                orderRepository.update(order);

                log.info("Outbox relay: published orderId={}", order.getOrderId());

            } catch (Exception e) {
                // Log and continue — will retry on next scheduled run
                log.error("Outbox relay failed for orderId={}: {}", order.getOrderId(), e.getMessage());
            }
        }
    }

    private record OutboxEvent(String eventId, String orderId, String customerId,
                                String deliveryAddress, Double amount,
                                String createdAt, String eventType) {}

    private OutboxEvent buildEvent(Order order) {
        return new OutboxEvent(
                java.util.UUID.randomUUID().toString(),
                order.getOrderId(),
                order.getCustomerId(),
                order.getDeliveryAddress(),
                order.getAmount(),
                order.getCreatedAt(),
                "ORDER_CREATED"
        );
    }
}
