package com.lastmile.orderhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lastmile.orderhub.exception.OrderNotFoundException;
import com.lastmile.orderhub.model.Order;
import com.lastmile.orderhub.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  OrderService orderService;

    @Test
    void createOrder_returns201() throws Exception {
        Order order = new Order();
        order.setOrderId("ORD-001");
        order.setStatus("PENDING");
        order.setPublishStatus("PENDING_PUBLISH");

        when(orderService.createOrder(any(), any(), any())).thenReturn(order);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("customerId", "CUST-001",
                                        "amount", 150.0,
                                        "deliveryAddress", "123 George St"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("ORD-001"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.publishStatus").value("PENDING_PUBLISH"));
    }

    @Test
    void createOrder_missingCustomerId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("amount", 150.0, "deliveryAddress", "123 George St"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void getOrder_notFound_returns404() throws Exception {
        when(orderService.getOrder("MISSING")).thenThrow(new OrderNotFoundException("MISSING"));

        mockMvc.perform(get("/api/v1/orders/MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/orders/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("order-hub"));
    }
}
