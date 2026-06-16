package com.lastmile.orderhub.service;

import com.lastmile.orderhub.exception.OrderNotFoundException;
import com.lastmile.orderhub.model.Order;
import com.lastmile.orderhub.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @InjectMocks OrderService orderService;

    @Test
    void createOrder_savesWithPendingPublish() {
        when(orderRepository.findByOrderId(any())).thenReturn(Optional.empty());
        doNothing().when(orderRepository).save(any());

        Order result = orderService.createOrder("CUST-001", 150.0, "123 George St");

        assertNotNull(result.getOrderId());
        assertEquals("PENDING", result.getStatus());
        assertEquals("PENDING_PUBLISH", result.getPublishStatus()); // outbox
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_idempotent_returnsExisting() {
        Order existing = new Order();
        existing.setOrderId("ORD-001");
        existing.setStatus("PENDING");

        when(orderRepository.findByOrderId(any())).thenReturn(Optional.of(existing));

        Order result = orderService.createOrder("CUST-001", 150.0, "123 George St");

        assertEquals("ORD-001", result.getOrderId());
        verify(orderRepository, never()).save(any()); // not saved again
    }

    @Test
    void getOrder_notFound_throws() {
        when(orderRepository.findByOrderId("MISSING")).thenReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class, () -> orderService.getOrder("MISSING"));
    }

    @Test
    void updateStatus_optimisticLock_conflict() {
        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setStatus("PENDING");

        when(orderRepository.findByOrderId("ORD-001")).thenReturn(Optional.of(order));
        when(orderRepository.update(any())).thenThrow(
                new OrderRepository.OptimisticLockException("Conflict"));

        assertThrows(OrderRepository.OptimisticLockException.class,
                () -> orderService.updateStatus("ORD-001", "CONFIRMED"));
    }

    @Test
    void updateStatus_success() {
        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setStatus("PENDING");

        Order updated = new Order();
        updated.setOrderId("ORD-001");
        updated.setStatus("CONFIRMED");

        when(orderRepository.findByOrderId("ORD-001")).thenReturn(Optional.of(order));
        when(orderRepository.update(any())).thenReturn(updated);

        Order result = orderService.updateStatus("ORD-001", "CONFIRMED");
        assertEquals("CONFIRMED", result.getStatus());
    }
}
