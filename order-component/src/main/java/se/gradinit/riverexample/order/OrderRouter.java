package se.gradinit.riverexample.order;

import java.rmi.RemoteException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import se.gradinit.river.platform.annotation.ExportedService;
import se.gradinit.river.platform.routing.HrwSelector;
import se.gradinit.river.platform.routing.RoutingKeys;
import se.gradinit.riverexample.support.ServiceLookup;

/**
 * Client-facing {@link OrderService}. Picks one backend with the platform HRW
 * selector on the {@code @Routing} field of {@link OrderRequest}. If that
 * backend does not answer, the call continues with another live backend.
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
            Map<ServiceID, ServiceItem> byId = new LinkedHashMap<>();
            for (ServiceItem item : backends) {
                if (item.serviceID != null && item.service != null) {
                    byId.put(item.serviceID, item);
                }
            }
            if (byId.isEmpty()) {
                throw new RemoteException("inga order-backend registrerade");
            }
            byte[] routingKey = RoutingKeys.canonicalBytes(request);
            ServiceID chosenId = HrwSelector.select(routingKey, new ArrayList<>(byId.keySet()));
            if (chosenId == null || !byId.containsKey(chosenId)) {
                throw new RemoteException("HRW valde ingen backend");
            }
            List<ServiceID> attempt = new ArrayList<>();
            attempt.add(chosenId);
            for (ServiceID id : byId.keySet()) {
                if (!id.equals(chosenId)) {
                    attempt.add(id);
                }
            }
            RemoteException last = null;
            for (ServiceID id : attempt) {
                try {
                    OrderConfirmation confirmation = ((OrderService) byId.get(id).service).place(request);
                    if (id.equals(chosenId)) {
                        System.out.println("ROUTE customer=" + request.customerId() + " backends=" + byId.size());
                    } else {
                        System.out.println("FAILOVER customer=" + request.customerId()
                                + " from=" + chosenId + " to=" + id);
                    }
                    return confirmation;
                } catch (RemoteException e) {
                    last = e;
                    System.out.println("BACKEND_FAIL " + id + " " + e.getClass().getSimpleName());
                }
            }
            throw last == null ? new RemoteException("ingen backend svarade") : last;
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteException("routning misslyckades", e);
        }
    }
}
