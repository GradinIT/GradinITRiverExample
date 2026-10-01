package se.gradinit.riverexample.order;

import java.rmi.RemoteException;
import java.time.Duration;
import java.util.UUID;
import net.jini.core.lookup.ServiceItem;
import se.gradinit.river.platform.annotation.ExportedService;
import se.gradinit.riverexample.customer.Customer;
import se.gradinit.riverexample.customer.CustomerService;
import se.gradinit.riverexample.support.ServiceLookup;

@ExportedService(name = "order-backend", remoteInterface = OrderService.class)
public class OrderBackend implements OrderService {
    private final String backendId = ServiceLookup.backendId();

    @Override
    public OrderConfirmation place(OrderRequest request) throws RemoteException {
        if (request == null || request.customerId() == null || request.customerId().isBlank()) {
            throw new RemoteException("customerId krävs");
        }
        if (request.quantity() <= 0) {
            throw new RemoteException("quantity måste vara > 0");
        }
        try {
            ServiceItem item = ServiceLookup.findOne(CustomerService.class, "customer", Duration.ofSeconds(20));
            Customer customer = ((CustomerService) item.service).find(request.customerId());
            if (!customer.active()) {
                throw new RemoteException("kunden är inte aktiv: " + request.customerId());
            }
            String orderId = UUID.randomUUID().toString();
            System.out.println("BACKEND " + backendId + " order=" + orderId + " customer=" + request.customerId());
            return new OrderConfirmation(orderId, request.customerId(), customer.name(), backendId);
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteException("kunde inte läsa kund " + request.customerId(), e);
        }
    }
}
