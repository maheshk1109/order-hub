package com.lastmile.orderhub.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

/**
 * Order entity — DynamoDB single-table design
 *
 * PK = ORDER#<orderId>
 * SK = METADATA
 *
 * OPTIMISTIC LOCKING:
 *   @DynamoDbVersionAttribute — DynamoDB auto-increments version on every write.
 *   If two processes read version=1 and both try to update:
 *     → First update succeeds → version becomes 2
 *     → Second update fails (ConditionalCheckFailedException) → must retry with fresh data
 *   Prevents lost updates without pessimistic locking (no DB locks held).
 *
 * OUTBOX:
 *   status=PENDING_PUBLISH means event not yet sent to SQS.
 *   Outbox relay scheduler reads PENDING_PUBLISH items → publishes → marks PUBLISHED.
 *   Even if app crashes after DB write but before SQS publish → outbox relay picks it up.
 */
@DynamoDbBean
public class Order {

    private String pk;           // ORDER#<orderId>
    private String sk;           // METADATA
    private String orderId;
    private String customerId;
    private String status;       // PENDING, CONFIRMED, DISPATCHED, DELIVERED, CANCELLED
    private Double amount;
    private String deliveryAddress;
    private String createdAt;
    private Integer version;     // optimistic locking
    private String publishStatus; // PENDING_PUBLISH, PUBLISHED (outbox pattern)
    private Long ttl;

    @DynamoDbPartitionKey
    public String getPk()                  { return pk; }
    public void setPk(String v)            { this.pk = v; }

    @DynamoDbSortKey
    public String getSk()                  { return sk; }
    public void setSk(String v)            { this.sk = v; }

    public String getOrderId()             { return orderId; }
    public void setOrderId(String v)       { this.orderId = v; }

    public String getCustomerId()          { return customerId; }
    public void setCustomerId(String v)    { this.customerId = v; }

    public String getStatus()              { return status; }
    public void setStatus(String v)        { this.status = v; }

    public Double getAmount()              { return amount; }
    public void setAmount(Double v)        { this.amount = v; }

    public String getDeliveryAddress()     { return deliveryAddress; }
    public void setDeliveryAddress(String v) { this.deliveryAddress = v; }

    public String getCreatedAt()           { return createdAt; }
    public void setCreatedAt(String v)     { this.createdAt = v; }

    @DynamoDbVersionAttribute
    public Integer getVersion()            { return version; }
    public void setVersion(Integer v)      { this.version = v; }

    @DynamoDbAttribute("publishStatus")
    public String getPublishStatus()       { return publishStatus; }
    public void setPublishStatus(String v) { this.publishStatus = v; }

    @DynamoDbAttribute("ttl")
    public Long getTtl()                   { return ttl; }
    public void setTtl(Long v)             { this.ttl = v; }
}
