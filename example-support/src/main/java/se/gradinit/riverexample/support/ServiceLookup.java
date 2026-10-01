package se.gradinit.riverexample.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceMatches;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.DiscoveryManagement;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.lookup.entry.Name;

/** Lookup by remote interface and Jini {@link Name}, the same key as a component dependency. */
public final class ServiceLookup {
    private ServiceLookup() {}

    public static ServiceRegistrar awaitRegistrar(DiscoveryManagement discovery, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ServiceRegistrar[] registrars = discovery.getRegistrars();
            if (registrars.length > 0) {
                return registrars[0];
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Ingen lookup-tjänst (Reggie) inom " + timeout.toSeconds() + " s");
    }

    public static ServiceItem findOne(Class<?> type, String jiniName, Duration timeout) throws Exception {
        List<ServiceItem> all = findAll(type, jiniName, timeout);
        if (all.isEmpty()) {
            throw new IllegalStateException("Ingen tjänst " + type.getName() + " name=" + jiniName);
        }
        return all.get(0);
    }

    /**
     * Uses {@link ServiceDiscoveryManager} first and falls back to
     * {@link ServiceRegistrar#lookup(ServiceTemplate, int)} so both client styles are covered.
     */
    public static List<ServiceItem> findAll(Class<?> type, String jiniName, Duration timeout) throws Exception {
        DiscoveryManagement discovery = Discovery.open();
        ServiceDiscoveryManager manager = new ServiceDiscoveryManager(discovery, null);
        try {
            ServiceTemplate template = template(type, jiniName);
            ServiceItem first = manager.lookup(template, null, timeout.toMillis());
            ServiceRegistrar registrar = first == null
                    ? awaitRegistrar(discovery, timeout)
                    : awaitRegistrar(discovery, Duration.ofSeconds(5));
            ServiceMatches matches = registrar.lookup(template, Integer.MAX_VALUE);
            List<ServiceItem> items = new ArrayList<>();
            if (matches.items != null) {
                for (ServiceItem item : matches.items) {
                    if (item != null && item.service != null) {
                        items.add(item);
                    }
                }
            }
            if (items.isEmpty() && first != null) {
                items.add(first);
            }
            return items;
        } finally {
            manager.terminate();
            discovery.terminate();
        }
    }

    public static ServiceTemplate template(Class<?> type, String jiniName) {
        return new ServiceTemplate(null, new Class<?>[] {type}, new Entry[] {new Name(jiniName)});
    }

    public static String backendId() {
        String configured = System.getProperty("river.instance");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return Long.toString(ProcessHandle.current().pid());
    }
}
