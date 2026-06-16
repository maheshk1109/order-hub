package com.lastmile.orderhub.repository;

import com.lastmile.orderhub.model.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.List;
import java.util.Optional;

/**
 * DynamoDB repository for Order entities.
 *
 * OPTIMISTIC LOCKING:
 *   update() uses @DynamoDbVersionAttribute — DynamoDB adds a condition:
 *   "only update if version = current version"
 *   If another process updated first → ConditionalCheckFailedException
 *   → caller catches and retries with fresh data
 *
 * OUTBOX PATTERN:
 *   findPendingPublish() returns orders with publishStatus=PENDING_PUBLISH
 *   Used by OutboxRelayScheduler to find undelivered events and publish to SQS
 */
@Repository
public class OrderRepository {

    private final DynamoDbTable<Order> table;

    public OrderRepository(DynamoDbEnhancedClient enhancedClient,
                           @Value("${aws.dynamodb.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Order.class));
    }

    public void save(Order order) {
        table.putItem(order);
    }

    public Optional<Order> findByOrderId(String orderId) {
        Order order = table.getItem(Key.builder()
                .partitionValue("ORDER#" + orderId)
                .sortValue("METADATA")
                .build());
        return Optional.ofNullable(order);
    }

    // update with optimistic locking — throws ConditionalCheckFailedException on conflict
    public Order update(Order order) {
        try {
            return table.updateItem(r -> r.item(order));
        } catch (ConditionalCheckFailedException e) {
            throw new OptimisticLockException("Concurrent update detected for order: "
                    + order.getOrderId() + ". Please retry.");
        }
    }

    // find all orders with PENDING_PUBLISH status — used by outbox relay
    public List<Order> findPendingPublish() {
        // Scan with filter — in production use a GSI on publishStatus for efficiency
        return table.scan(r -> r.filterExpression(
                software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                        .expression("publishStatus = :status")
                        .putExpressionValue(":status",
                                software.amazon.awssdk.services.dynamodb.model.AttributeValue
                                        .fromS("PENDING_PUBLISH"))
                        .build()))
                .items()
                .stream()
                .toList();
    }

    public static class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) { super(message); }
    }
}
