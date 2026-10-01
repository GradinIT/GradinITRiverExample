package se.gradinit.riverexample.customer;

import java.rmi.RemoteException;
import java.util.Map;
import se.gradinit.river.platform.annotation.ExportedService;

@ExportedService(name = "customer", remoteInterface = CustomerService.class)
public class CustomerServiceImpl implements CustomerService {
    private static final Map<String, Customer> CUSTOMERS = Map.of(
            "alice", new Customer("alice", "Alice Andersson", true),
            "bob", new Customer("bob", "Bob Berg", true),
            "inactive", new Customer("inactive", "Inaktiv Kund", false));

    @Override
    public Customer find(String customerId) throws RemoteException {
        Customer customer = CUSTOMERS.get(customerId);
        if (customer == null) {
            return new Customer(customerId, "", false);
        }
        return customer;
    }
}
