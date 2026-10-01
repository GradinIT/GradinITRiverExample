package se.gradinit.riverexample.support;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.zip.CRC32;

/**
 * Highest-random-weight choice on a routing key. The weight is a stable CRC32
 * of {@code key + nodeId}, so the same customer always lands on the same backend
 * for a given set of nodes.
 */
public final class Hrw {
    private Hrw() {}

    public static <T> T choose(String key, List<T> nodes, Function<T, String> nodeId) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("inga noder att välja mellan");
        }
        T best = null;
        long bestWeight = Long.MIN_VALUE;
        String bestId = null;
        for (T node : nodes) {
            String id = nodeId.apply(node);
            long weight = weight(key, id);
            if (best == null || weight > bestWeight || (weight == bestWeight && id.compareTo(bestId) < 0)) {
                best = node;
                bestWeight = weight;
                bestId = id;
            }
        }
        return best;
    }

    static long weight(String key, String nodeId) {
        CRC32 crc = new CRC32();
        crc.update(key.getBytes(StandardCharsets.UTF_8));
        crc.update((byte) '|');
        crc.update(nodeId.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}
