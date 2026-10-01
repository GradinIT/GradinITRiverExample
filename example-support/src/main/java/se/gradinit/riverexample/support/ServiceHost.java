package se.gradinit.riverexample.support;

import java.rmi.Remote;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.DiscoveryManagement;
import net.jini.export.Exporter;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import net.jini.lookup.JoinManager;
import net.jini.lookup.entry.Name;

/**
 * Exports a remote object with {@link BasicJeriExporter}, joins Reggie through
 * {@link JoinManager} and renews the lease for the life of the process.
 */
public final class ServiceHost {
    private ServiceHost() {}

    public static void serve(Remote implementation, String jiniName) throws Exception {
        Exporter exporter = new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(), false, false);
        Remote proxy = exporter.export(implementation);
        DiscoveryManagement discovery = Discovery.open();
        PathAndId stored = new PathAndId(ServiceIds.file(jiniName));
        ServiceID existing = ServiceIds.read(stored.path);
        Entry[] attributes = new Entry[] {new Name(jiniName)};
        JoinManager join = existing == null
                ? new JoinManager(proxy, attributes, id -> persist(stored, id), discovery, null)
                : new JoinManager(proxy, attributes, existing, discovery, null);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                join.terminate();
            } catch (RuntimeException ignored) {
                // process is exiting
            }
            discovery.terminate();
            try {
                exporter.unexport(true);
            } catch (RuntimeException ignored) {
                // process is exiting
            }
        }));
        System.out.println("EXPORTED name=" + jiniName + " class=" + implementation.getClass().getName());
        Thread.currentThread().join();
    }

    private static void persist(PathAndId stored, ServiceID id) {
        try {
            ServiceIds.write(stored.path, id);
            System.out.println("SERVICE_ID " + id);
        } catch (Exception e) {
            throw new IllegalStateException("kunde inte spara ServiceID i " + stored.path, e);
        }
    }

    private record PathAndId(java.nio.file.Path path) {}
}
