package se.gradinit.riverexample.customer;

import java.io.Serializable;

public record Customer(String customerId, String name, boolean active) implements Serializable {}
