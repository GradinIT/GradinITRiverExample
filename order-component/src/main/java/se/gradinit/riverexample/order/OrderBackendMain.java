package se.gradinit.riverexample.order;

import se.gradinit.riverexample.support.ServiceHost;

public final class OrderBackendMain {
    private OrderBackendMain() {}

    public static void main(String[] args) throws Exception {
        ServiceHost.serve(new OrderBackend(), args);
    }
}
