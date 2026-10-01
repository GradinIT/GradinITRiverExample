package se.gradinit.riverexample.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Starts GradinITRiver, deploys customer and order, calls the client, monitors and undeploys.
 * Platform jars come from GitHub Packages ({@code se.gradinit.river}, server id {@code github}).
 */
class OrderPlatformIT {
    private Process bootstrap;

    @AfterEach
    void stopBootstrap() {
        if (bootstrap != null) {
            bootstrap.destroy();
            bootstrap = null;
        }
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void deployCallMonitorUndeploy() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        if (!Files.isDirectory(repoRoot.resolve("client"))) {
            repoRoot = Path.of(System.getProperty("user.dir"));
        }
        String version = System.getProperty("gradinit.river.version", "3.0.0-gradinit-SNAPSHOT");
        Path bootstrapJar = configuredOrResolved("river.bootstrap.jar", "platform-bootstrap", version);
        Path cliJar = configuredOrResolved("river.cli.jar", "platform-cli", version);
        Path compatJar = configuredOrResolved("river.compat.jar", "compat-rmi-activation", version);
        assertTrue(Files.isRegularFile(bootstrapJar), "saknar " + bootstrapJar
                + " — lös se.gradinit.river från GitHub Packages (docs/beroenden.md)");
        assertTrue(Files.isRegularFile(cliJar), "saknar " + cliJar);
        assertTrue(Files.isRegularFile(compatJar), "saknar " + compatJar);

        Path customerJar = repoRoot.resolve("customer-component/target/customer-component-1.0.0.jar");
        Path orderJar = repoRoot.resolve("order-component/target/order-component-1.0.0.jar");
        Path clientJar = repoRoot.resolve("client/target/client-1.0.0.jar");
        Path classpathFile = repoRoot.resolve("client/target/classpath.txt");
        assertTrue(Files.isRegularFile(customerJar), "saknar " + customerJar);
        assertTrue(Files.isRegularFile(orderJar), "saknar " + orderJar);
        assertTrue(Files.isRegularFile(clientJar), "saknar " + clientJar);

        Path logs = repoRoot.resolve("integration-tests/target/platform-logs");
        Files.createDirectories(logs);
        List<String> jvm = jvmFlags(compatJar);
        String platformClasspath = platformClasspath();
        String bootstrapMain = mainClass(bootstrapJar, platformClasspath);
        String cliMain = mainClass(cliJar, platformClasspath);
        System.out.println("BOOTSTRAP_MAIN " + bootstrapMain);
        System.out.println("CLI_MAIN " + cliMain);

        Path bootstrapLog = logs.resolve("bootstrap.log");
        bootstrap = start(bootstrapLog, repoRoot, jvm, "-cp", platformClasspath, bootstrapMain);
        Thread.sleep(Duration.ofSeconds(15).toMillis());
        assertTrue(bootstrap.isAlive(), "bootstrap dog\n" + Files.readString(bootstrapLog));

        CommandResult deployedCustomer = river(logs, repoRoot, jvm, platformClasspath, cliMain, "deploy", customerJar.toString());
        assertEquals(0, deployedCustomer.exit, deployedCustomer.output);
        CommandResult deployedOrder = river(logs, repoRoot, jvm, platformClasspath, cliMain, "deploy", orderJar.toString());
        assertEquals(0, deployedOrder.exit, deployedOrder.output);

        CommandResult monitor = river(logs, repoRoot, jvm, platformClasspath, cliMain, "monitor");
        assertEquals(0, monitor.exit, monitor.output);
        assertTrue(!monitor.output.isBlank(), "river monitor gav tom utdata");

        String classpath = Files.readString(classpathFile).trim();
        String clientClasspath = clientJar + System.getProperty("path.separator") + classpath;
        CommandResult call = java(logs.resolve("client.log"), repoRoot, jvm, "-cp", clientClasspath,
                "se.gradinit.riverexample.client.OrderClient", "alice", "SKU-100", "1");
        assertEquals(0, call.exit, call.output);
        List<String> ok = call.output.lines().filter(line -> line.startsWith("ORDER_OK ")).toList();
        assertEquals(2, ok.size(), call.output);
        String backend = ok.get(0).replaceAll(".*backendId=", "");
        assertTrue(ok.get(1).endsWith("backendId=" + backend), call.output);

        CommandResult undeployOrder = river(logs, repoRoot, jvm, platformClasspath, cliMain, "undeploy", "order");
        if (undeployOrder.exit != 0) {
            undeployOrder = river(logs, repoRoot, jvm, platformClasspath, cliMain, "undeploy", orderJar.toString());
        }
        assertEquals(0, undeployOrder.exit, undeployOrder.output);
        CommandResult undeployCustomer = river(logs, repoRoot, jvm, platformClasspath, cliMain, "undeploy", "customer");
        if (undeployCustomer.exit != 0) {
            undeployCustomer = river(logs, repoRoot, jvm, platformClasspath, cliMain, "undeploy", customerJar.toString());
        }
        assertEquals(0, undeployCustomer.exit, undeployCustomer.output);
    }

    private static Path configuredOrResolved(String property, String artifact, String version) throws IOException {
        String configured = System.getProperty(property);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        Path dir = Path.of(System.getProperty("user.home"), ".m2", "repository", "se", "gradinit", "river", artifact, version);
        if (!Files.isDirectory(dir)) {
            return dir.resolve(artifact + "-" + version + ".jar");
        }
        Path newest = null;
        try (var candidates = Files.newDirectoryStream(dir, artifact + "-*.jar")) {
            for (Path candidate : candidates) {
                String name = candidate.getFileName().toString();
                if (name.endsWith("-sources.jar") || name.endsWith("-javadoc.jar") || name.endsWith("-tests.jar")) {
                    continue;
                }
                if (newest == null || Files.getLastModifiedTime(candidate).compareTo(Files.getLastModifiedTime(newest)) > 0) {
                    newest = candidate;
                }
            }
        }
        return newest == null ? dir.resolve(artifact + "-" + version + ".jar") : newest;
    }

    private static String platformClasspath() throws IOException {
        String configured = System.getProperty("river.platform.classpath", "target/platform-classpath.txt");
        Path file = Path.of(configured);
        assertTrue(Files.isRegularFile(file), "saknar plattformens klassökväg " + file.toAbsolutePath());
        String classpath = Files.readString(file).trim();
        assertTrue(!classpath.isBlank(), "tom klassökväg i " + file);
        return classpath;
    }

    private static String mainClass(Path jar, String classpath) throws IOException {
        String fromManifest = manifestMain(jar);
        if (fromManifest != null) {
            return fromManifest;
        }
        List<String> inJar = mainsIn(jar);
        if (!inJar.isEmpty()) {
            return preferMain(inJar);
        }
        List<String> platformMains = new ArrayList<>();
        for (String entry : classpath.split(System.getProperty("path.separator"))) {
            Path candidate = Path.of(entry);
            if (!Files.isRegularFile(candidate) || !candidate.getFileName().toString().endsWith(".jar")) {
                continue;
            }
            for (String name : mainsIn(candidate)) {
                if (name.startsWith("se.gradinit.river.platform.bootstrap.")
                        || name.equals("com.sun.jini.start.ServiceStarter")) {
                    platformMains.add(name);
                }
            }
        }
        assertTrue(!platformMains.isEmpty(), "ingen startklass i " + jar + " eller på plattformens klassökväg");
        return preferMain(platformMains);
    }

    private static String manifestMain(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            var manifest = file.getManifest();
            if (manifest == null) {
                return null;
            }
            String main = manifest.getMainAttributes().getValue("Main-Class");
            return main == null || main.isBlank() ? null : main.trim();
        }
    }

    private static String preferMain(List<String> names) {
        return names.stream()
                .min(Comparator.comparingInt(OrderPlatformIT::mainRank))
                .orElseThrow();
    }

    private static int mainRank(String name) {
        if (name.startsWith("se.gradinit.river.platform.bootstrap.")) {
            return 0;
        }
        if (name.endsWith("Bootstrap") || name.endsWith("Main")) {
            return 1;
        }
        if (name.equals("com.sun.jini.start.ServiceStarter")) {
            return 2;
        }
        return 3;
    }

    private static List<String> mainsIn(Path jar) throws IOException {
        List<String> found = new ArrayList<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            var entries = file.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.contains("$") || name.startsWith("META-INF/versions/")) {
                    continue;
                }
                try (InputStream in = file.getInputStream(entry)) {
                    if (hasPublicStaticMain(in.readAllBytes())) {
                        found.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
                    }
                } catch (RuntimeException ignored) {
                    // skip a class file the scanner cannot parse
                }
            }
        }
        return found;
    }

    private static boolean hasPublicStaticMain(byte[] bytes) {
        if (bytes.length < 24 || (bytes[0] & 0xff) != 0xca || (bytes[1] & 0xff) != 0xfe) {
            return false;
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.position(8);
        int count = buf.getShort() & 0xffff;
        String[] utf8 = new String[count];
        for (int i = 1; i < count; i++) {
            int tag = buf.get() & 0xff;
            switch (tag) {
                case 1 -> {
                    int len = buf.getShort() & 0xffff;
                    byte[] data = new byte[len];
                    buf.get(data);
                    utf8[i] = new String(data, StandardCharsets.UTF_8);
                }
                case 7, 8, 16, 19, 20 -> buf.getShort();
                case 15 -> {
                    buf.get();
                    buf.getShort();
                }
                case 3, 4, 9, 10, 11, 12, 17, 18 -> buf.getInt();
                case 5, 6 -> {
                    buf.getLong();
                    i++;
                }
                default -> {
                    return false;
                }
            }
        }
        buf.getShort();
        buf.getShort();
        buf.getShort();
        int interfaces = buf.getShort() & 0xffff;
        buf.position(buf.position() + interfaces * 2);
        int fields = buf.getShort() & 0xffff;
        for (int i = 0; i < fields; i++) {
            skipMember(buf);
        }
        int methods = buf.getShort() & 0xffff;
        for (int i = 0; i < methods; i++) {
            int access = buf.getShort() & 0xffff;
            int nameIndex = buf.getShort() & 0xffff;
            int descIndex = buf.getShort() & 0xffff;
            skipAttributes(buf);
            if ((access & 0x0001) != 0
                    && (access & 0x0008) != 0
                    && "main".equals(utf8[nameIndex])
                    && "([Ljava/lang/String;)V".equals(utf8[descIndex])) {
                return true;
            }
        }
        return false;
    }

    private static void skipMember(ByteBuffer buf) {
        buf.getShort();
        buf.getShort();
        buf.getShort();
        skipAttributes(buf);
    }

    private static void skipAttributes(ByteBuffer buf) {
        int attributes = buf.getShort() & 0xffff;
        for (int i = 0; i < attributes; i++) {
            buf.getShort();
            int length = buf.getInt();
            buf.position(buf.position() + length);
        }
    }

    private static List<String> jvmFlags(Path compatJar) {
        return List.of(
                "--patch-module", "java.rmi=" + compatJar,
                "--add-exports", "java.rmi/java.rmi.activation=ALL-UNNAMED");
    }

    private static Process start(Path log, Path work, List<String> jvm, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvm);
        command.addAll(List.of(args));
        return new ProcessBuilder(command)
                .directory(work.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    private static CommandResult river(Path logs, Path work, List<String> jvm, String classpath, String mainClass, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);
        command.addAll(List.of(args));
        Path log = logs.resolve(String.join("-", args).replaceAll("[^a-zA-Z0-9._-]", "_") + ".log");
        return run(log, work, jvm, command.toArray(String[]::new));
    }

    private static CommandResult java(Path log, Path work, List<String> jvm, String... args) throws Exception {
        return run(log, work, jvm, args);
    }

    private static CommandResult run(Path log, Path work, List<String> jvm, String... args) throws Exception {
        Process process = start(log, work, jvm, args);
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            return new CommandResult(-1, Files.readString(log) + "\nTIMEOUT");
        }
        return new CommandResult(process.exitValue(), Files.readString(log));
    }

    private record CommandResult(int exit, String output) {}
}
