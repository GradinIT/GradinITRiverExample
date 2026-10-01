package se.gradinit.riverexample.order;

import java.io.Serializable;

public record OrderConfirmation(String orderId, String customerId, String customerName, String backendId)
        implements Serializable {}
