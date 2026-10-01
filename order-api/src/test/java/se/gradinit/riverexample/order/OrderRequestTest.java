package se.gradinit.riverexample.order;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Test;
import se.gradinit.river.platform.annotation.Routing;

class OrderRequestTest {
    @Test
    void customerIdCarriesRoutingAnnotation() {
        boolean found = false;
        for (RecordComponent component : OrderRequest.class.getRecordComponents()) {
            if ("customerId".equals(component.getName()) && component.getAnnotation(Routing.class) != null) {
                found = true;
            }
        }
        if (!found) {
            try {
                found = OrderRequest.class.getDeclaredField("customerId").getAnnotation(Routing.class) != null;
            } catch (NoSuchFieldException e) {
                found = false;
            }
        }
        assertTrue(found, "@Routing saknas på customerId");
        assertNotNull(new OrderRequest("alice", "SKU-100", 1).customerId());
    }
}
