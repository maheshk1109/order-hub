package com.lastmile.orderhub.controller;

import com.lastmile.orderhub.model.Order;
import com.lastmile.orderhub.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Order Hub REST API
 *
 * POST /orders     → create order (idempotent, outbox pattern)
 * GET  /orders/:id → get order by ID
 * PUT  /orders/:id/status → update status (optimistic locking)
 */
@Tag(name = "Orders", description = "Order Hub — create and manage orders")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Create order — persists to DynamoDB + outbox (guarantees SQS publish)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(
                request.customerId(),
                request.amount(),
                request.deliveryAddress());
    }

    @Operation(summary = "Get order by ID")
    @GetMapping("/{orderId}")
    public Order getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId);
    }

    @Operation(summary = "Update order status — uses optimistic locking (409 on conflict)")
    @PutMapping("/{orderId}/status")
    public Order updateStatus(@PathVariable String orderId,
                              @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isBlank())
            throw new IllegalArgumentException("status is required");
        return orderService.updateStatus(orderId, status);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("service", "order-hub", "status", "UP");
    }

    record CreateOrderRequest(
            @NotBlank(message = "customerId is required") String customerId,
            @NotNull(message = "amount is required") @Min(1) Double amount,
            @NotBlank(message = "deliveryAddress is required") String deliveryAddress
    ) {}
}
