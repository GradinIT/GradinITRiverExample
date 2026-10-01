package se.gradinit.riverexample.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jini.core.lookup.ServiceID;

/** Persists a Jini {@link ServiceID} so a restarted instance keeps the same id. */
public final class ServiceIds {
    private ServiceIds() {}

    public static Path file(String jiniName) {
        String instance = System.getProperty("river.instance", "0");
        String dir = System.getProperty("river.serviceId.dir", "var");
        String safe = jiniName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return Path.of(dir, safe + "-" + instance + ".serviceid");
    }

    public static ServiceID read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String text = Files.readString(file).trim();
        String[] parts = text.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return new ServiceID(Long.parseUnsignedLong(parts[0], 16), Long.parseUnsignedLong(parts[1], 16));
    }

    public static void write(Path file, ServiceID id) throws IOException {
        Files.createDirectories(file.getParent());
        String text = Long.toHexString(id.getMostSignificantBits()) + ":" + Long.toHexString(id.getLeastSignificantBits());
        Files.writeString(file, text);
    }
}
