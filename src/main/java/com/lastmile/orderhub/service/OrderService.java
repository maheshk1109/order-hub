package com.lastmile.orderhub.service;

import com.lastmile.orderhub.exception.DuplicateOrderException;
import com.lastmile.orderhub.exception.OrderNotFoundException;
import com.lastmile.orderhub.model.Order;
import com.lastmile.orderhub.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Order business logic.
 *
 * IDEMPOTENT API:
 *   createOrder checks if orderId already exists before saving.
 *   Same request twice → returns existing order (not an error, not a duplicate).
 *   Safe for client retries.
 *
 * STRUCTURED LOGGING WITH MDC:
 *   MDC.put("orderId", ...) adds orderId to EVERY log line in this thread.
 *   In production: traceId comes from incoming HTTP header (X-Trace-Id).
 *   All logs for one request have same traceId → easy to correlate in CloudWatch.
 *
 * OUTBOX:
 *   Order saved with publishStatus=PENDING_PUBLISH.
 *   OutboxRelayScheduler picks it up and publishes to SQS asynchronously.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public Order createOrder(String customerId, Double amount, String deliveryAddress) {
        String orderId = UUID.randomUUID().toString();

        // Add to MDC — appears in every log line for this request
        MDC.put("orderId", orderId);
        MDC.put("customerId", customerId);

        try {
            // Idempotency — check if already exists (e.g. client retry)
            if (orderRepository.findByOrderId(orderId).isPresent()) {
                log.info("Duplicate order request — returning existing order");
                return orderRepository.findByOrderId(orderId).get();
            }

            Order order = new Order();
            order.setPk("ORDER#" + orderId);
            order.setSk("METADATA");
            order.setOrderId(orderId);
            order.setCustomerId(customerId);
            order.setAmount(amount);
            order.setDeliveryAddress(deliveryAddress);
            order.setStatus("PENDING");
            order.setCreatedAt(Instant.now().toString());
            order.setPublishStatus("PENDING_PUBLISH"); // outbox pattern
            order.setTtl(Instant.now().plusSeconds(90 * 24 * 60 * 60).getEpochSecond()); // 90 days

            orderRepository.save(order);
            log.info("Order created with status=PENDING, publishStatus=PENDING_PUBLISH");

            return order;

        } finally {
            MDC.clear();
        }
    }

    public Order getOrder(String orderId) {
        MDC.put("orderId", orderId);
        try {
            return orderRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));
        } finally {
            MDC.clear();
        }
    }

    public Order updateStatus(String orderId, String newStatus) {
        MDC.put("orderId", orderId);
        try {
            Order order = orderRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            order.setStatus(newStatus);

            // update() uses optimistic locking — throws OptimisticLockException on conflict
            Order updated = orderRepository.update(order);
            log.info("Order status updated to {}", newStatus);
            return updated;

        } finally {
            MDC.clear();
        }
    }
}
