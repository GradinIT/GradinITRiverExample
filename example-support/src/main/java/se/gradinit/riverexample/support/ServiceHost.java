package se.gradinit.riverexample.support;

import java.nio.file.Path;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.jeri.BasicILFactory;
import net.jini.jeri.BasicJeriExporter;
import net.jini.jeri.tcp.TcpServerEndpoint;
import se.gradinit.river.platform.annotation.ExportedService;
import se.gradinit.river.platform.export.ServiceExporter;
import se.gradinit.river.platform.identity.ServiceIdFile;

/**
 * Exports an {@code @ExportedService} with {@link ServiceExporter#joinAnnotated}.
 * The platform persists the Jini {@link ServiceID} via {@link ServiceIdFile}.
 */
public final class ServiceHost {
    private static final long LOOKUP_WAIT_MILLIS = 60_000L;

    private ServiceHost() {}

    public static void serve(Object implementation, String... args) throws Exception {
        ServiceExporter.requireExported(implementation);
        String instanceId = instanceId(args);
        Path serviceIdFile = ServiceIdFile.defaultPath(serviceIdKey(implementation, instanceId));
        if (ServiceIdFile.exists(serviceIdFile)) {
            ServiceID existing = ServiceIdFile.read(serviceIdFile);
            ServiceIdFile.write(serviceIdFile, existing);
            System.out.println("SERVICE_ID " + existing);
        }
        ExportedService marked = implementation.getClass().getAnnotation(ExportedService.class);
        String name = marked == null ? null : marked.name();
        if (name != null && name.isBlank()) {
            name = null;
        }
        LookupDiscoveryManager discovery = Discovery.open();
        ServiceExporter.Running running = ServiceExporter.joinAnnotated(
                discovery,
                () -> new BasicJeriExporter(TcpServerEndpoint.getInstance(0), new BasicILFactory(), false, false),
                name,
                serviceIdFile,
                new Entry[0],
                LOOKUP_WAIT_MILLIS,
                implementation);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(running, discovery)));
        Object exported = running.only();
        System.out.println("EXPORT " + ServiceExporter.describe(implementation.getClass()));
        System.out.println("PUBLISHED " + running.published());
        System.out.println("SKIPPED " + running.skipped());
        if (ServiceIdFile.exists(serviceIdFile)) {
            System.out.println("SERVICE_ID " + ServiceIdFile.read(serviceIdFile));
        }
        System.out.println("JOINED " + exported);
        Thread.currentThread().join();
    }

    private static void shutdown(ServiceExporter.Running running, LookupDiscoveryManager discovery) {
        try {
            running.unexport();
        } catch (Exception ignored) {
            // process is exiting
        }
        try {
            running.close();
        } catch (Exception ignored) {
            // process is exiting
        }
        try {
            discovery.terminate();
        } catch (Exception ignored) {
            // process is exiting
        }
    }

    /**
     * Instance index from {@code -Driver.instance}, otherwise the stem of the
     * supervisor config argument ({@code order-backend/1.config} → {@code 1}).
     * A shared default of {@code 0} would make every component reuse one ServiceID.
     */
    static String instanceId(String... args) {
        String configured = System.getProperty("river.instance");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (args != null) {
            for (String arg : args) {
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                String fileName = Path.of(arg).getFileName().toString();
                String suffix = ".config";
                if (fileName.endsWith(suffix) && fileName.length() > suffix.length()) {
                    return fileName.substring(0, fileName.length() - suffix.length());
                }
            }
        }
        return Long.toString(ProcessHandle.current().pid());
    }

    /** Jini name plus instance, so customer/0 and order-backend/0 do not share a file. */
    static String serviceIdKey(Object implementation, String instanceId) {
        ExportedService marked = implementation.getClass().getAnnotation(ExportedService.class);
        String name = marked == null ? "" : marked.name();
        if (name == null || name.isBlank()) {
            name = implementation.getClass().getSimpleName();
        }
        String instance = instanceId == null || instanceId.isBlank() ? "0" : instanceId;
        return (name + "-" + instance).replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
