package se.gradinit.riverexample.order;

import se.gradinit.riverexample.support.ServiceHost;

public final class OrderRouterMain {
    private OrderRouterMain() {}

    public static void main(String[] args) throws Exception {
        ServiceHost.serve(new OrderRouter(), "order");
    }
}
