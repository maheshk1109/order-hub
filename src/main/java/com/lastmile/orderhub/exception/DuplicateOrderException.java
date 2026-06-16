package com.lastmile.orderhub.exception;

public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String orderId) {
        super("Order already exists: " + orderId);
    }
}
