package se.gradinit.riverexample.customer;

import se.gradinit.riverexample.support.ServiceHost;

public final class CustomerMain {
    private CustomerMain() {}

    public static void main(String[] args) throws Exception {
        ServiceHost.serve(new CustomerServiceImpl());
    }
}
