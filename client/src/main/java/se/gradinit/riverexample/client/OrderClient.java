package se.gradinit.riverexample.client;

import java.time.Duration;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.DiscoveryManagement;
import net.jini.lookup.ServiceDiscoveryManager;
import se.gradinit.riverexample.order.OrderConfirmation;
import se.gradinit.riverexample.order.OrderRequest;
import se.gradinit.riverexample.order.OrderService;
import se.gradinit.riverexample.support.Discovery;
import se.gradinit.riverexample.support.ServiceLookup;

/**
 * Looks up {@link OrderService} + Name {@code order} and places the same order twice.
 * The two confirmations should name the same backend (HRW on customerId).
 */
public final class OrderClient {
    private OrderClient() {}

    public static void main(String[] args) throws Exception {
        String customerId = args.length > 0 ? args[0] : "alice";
        String sku = args.length > 1 ? args[1] : "SKU-100";
        int quantity = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        OrderRequest request = new OrderRequest(customerId, sku, quantity);

        DiscoveryManagement discovery = Discovery.open();
        ServiceDiscoveryManager manager = new ServiceDiscoveryManager(discovery, null);
        try {
            ServiceTemplate template = ServiceLookup.template(OrderService.class, "order");
            ServiceItem item = manager.lookup(template, null, Duration.ofSeconds(30).toMillis());
            if (item == null) {
                ServiceRegistrar registrar = ServiceLookup.awaitRegistrar(discovery, Duration.ofSeconds(30));
                item = registrar.lookup(template);
            }
            if (item == null || item.service == null) {
                throw new IllegalStateException("Hittade inte OrderService name=order");
            }
            OrderService orders = (OrderService) item.service;
            OrderConfirmation first = orders.place(request);
            OrderConfirmation second = orders.place(request);
            System.out.println(format(first));
            System.out.println(format(second));
            if (!first.backendId().equals(second.backendId())) {
                throw new IllegalStateException("HRW gav olika backend för samma kund: "
                        + first.backendId() + " vs " + second.backendId());
            }
        } finally {
            manager.terminate();
            discovery.terminate();
        }
    }

    static String format(OrderConfirmation confirmation) {
        return "ORDER_OK orderId=" + confirmation.orderId()
                + " customerId=" + confirmation.customerId()
                + " customerName=" + confirmation.customerName()
                + " backendId=" + confirmation.backendId();
    }
}
