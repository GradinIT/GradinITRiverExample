package se.gradinit.riverexample.order;

import java.io.Serializable;
import se.gradinit.river.platform.annotation.Routing;

/** {@code customerId} is the HRW routing key. sku and quantity are payload. */
public record OrderRequest(@Routing String customerId, String sku, int quantity) implements Serializable {}
