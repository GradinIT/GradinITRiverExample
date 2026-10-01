package se.gradinit.riverexample.customer;

import java.rmi.Remote;
import java.rmi.RemoteException;

/** Customer directory. Published in Reggie as interface + Jini Name {@code customer}. */
public interface CustomerService extends Remote {
    Customer find(String customerId) throws RemoteException;
}
