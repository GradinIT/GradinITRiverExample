package se.gradinit.riverexample.order;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface OrderService extends Remote {
    OrderConfirmation place(OrderRequest request) throws RemoteException;
}
