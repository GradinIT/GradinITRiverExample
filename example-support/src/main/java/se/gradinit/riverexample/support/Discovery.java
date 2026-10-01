package se.gradinit.riverexample.support;

import java.rmi.RemoteException;
import net.jini.core.discovery.LookupLocator;
import net.jini.discovery.DiscoveryManagement;
import net.jini.discovery.LookupDiscovery;
import net.jini.discovery.LookupDiscoveryManager;

/**
 * Multicast discovery plus an explicit lookup locator. GradinITRiver's default
 * Reggie listens on the Jini locator port unless {@code se.gradinit.river.lookup} says otherwise.
 */
public final class Discovery {
    public static final String LOOKUP_PROPERTY = "se.gradinit.river.lookup";
    public static final String DEFAULT_LOCATOR = "jini://127.0.0.1:4160";

    private Discovery() {}

    public static DiscoveryManagement open() throws RemoteException, java.io.IOException {
        String locator = System.getProperty(LOOKUP_PROPERTY, DEFAULT_LOCATOR);
        LookupLocator[] locators = locator.isBlank()
                ? null
                : new LookupLocator[] {new LookupLocator(locator)};
        return new LookupDiscoveryManager(LookupDiscovery.ALL_GROUPS, locators, null);
    }
}
