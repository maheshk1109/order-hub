package com.lastmile.orderhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Order Hub — Microservice 1
 *
 * Responsibilities:
 *   - Accept order creation via REST API
 *   - Persist order to DynamoDB
 *   - Publish order event to SQS (for dispatch-engine to consume)
 *   - Outbox pattern — guarantee message delivery even if app crashes mid-publish
 *   - Optimistic locking — prevent concurrent update conflicts
 *
 * Distributed Systems Concepts Demonstrated:
 *   - Outbox Pattern: write to DB + outbox table atomically, relay publishes separately
 *   - Optimistic Locking: version field prevents lost updates under concurrency
 *   - Idempotent API: same request twice = same result (using orderNumber as dedup key)
 */
@SpringBootApplication
@EnableScheduling
public class OrderHubApp {
    public static void main(String[] args) {
        SpringApplication.run(OrderHubApp.class, args);
    }
}
