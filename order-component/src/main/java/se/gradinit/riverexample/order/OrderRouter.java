package se.gradinit.riverexample.order;

import java.rmi.RemoteException;
import java.time.Duration;
import java.util.List;
import net.jini.core.lookup.ServiceItem;
import se.gradinit.river.platform.annotation.ExportedService;
import se.gradinit.riverexample.support.Hrw;
import se.gradinit.riverexample.support.ServiceLookup;

/**
 * Client-facing {@link OrderService}. Picks one of the backend instances with HRW
 * on the {@code @Routing} customer id.
 */
@ExportedService(name = "order", remoteInterface = OrderService.class)
public class OrderRouter implements OrderService {
    @Override
    public OrderConfirmation place(OrderRequest request) throws RemoteException {
        if (request == null || request.customerId() == null || request.customerId().isBlank()) {
            throw new RemoteException("customerId krävs");
        }
        try {
            List<ServiceItem> backends = ServiceLookup.findAll(OrderService.class, "order-backend", Duration.ofSeconds(20));
            if (backends.isEmpty()) {
                throw new RemoteException("inga order-backend registrerade");
            }
            ServiceItem chosen = Hrw.choose(
                    request.customerId(),
                    backends,
                    item -> item.serviceID == null ? item.service.toString() : item.serviceID.toString());
            OrderService backend = (OrderService) chosen.service;
            System.out.println("ROUTE customer=" + request.customerId() + " backends=" + backends.size());
            return backend.place(request);
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteException("routning misslyckades", e);
        }
    }
}
