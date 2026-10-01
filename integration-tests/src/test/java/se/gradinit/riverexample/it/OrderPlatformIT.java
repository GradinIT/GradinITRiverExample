package se.gradinit.riverexample.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Starts GradinITRiver, deploys customer and order, calls the client, monitors and undeploys.
 * Platform jars come from GitHub Packages ({@code se.gradinit.river}, server id {@code github}).
 *
 * <p>Skipped until {@code platform-bootstrap} publishes {@code PlatformMain} with {@code Main-Class}
 * and prints {@code RIVER_PLATFORM_READY}. The current snapshot has no entry point, and
 * {@code PlatformRuntime.activationClasses()} only resolves inside the GradinITRiver build tree.
 */
@Disabled("Väntar på PlatformMain i publicerad platform-bootstrap (Main-Class och RIVER_PLATFORM_READY). Nuvarande SNAPSHOT har ingen startpunkt.")
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
        String bootstrapMain = mainClass(bootstrapJar);
        String cliMain = mainClass(cliJar);

        Path bootstrapLog = logs.resolve("bootstrap.log");
        bootstrap = start(bootstrapLog, repoRoot, jvm, "-cp", platformClasspath, bootstrapMain);
        assertTrue(awaitReady(bootstrap, bootstrapLog, Duration.ofSeconds(60)),
                "bootstrap blev inte RIVER_PLATFORM_READY\n" + Files.readString(bootstrapLog));

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

    private static String mainClass(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            var manifest = file.getManifest();
            String main = manifest == null ? null : manifest.getMainAttributes().getValue("Main-Class");
            assertTrue(main != null && !main.isBlank(), "ingen Main-Class i " + jar
                    + " — kräver PlatformMain i publicerad platform-bootstrap");
            return main.trim();
        }
    }

    private static boolean awaitReady(Process process, Path log, Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(log) && Files.readString(log).contains("RIVER_PLATFORM_READY")) {
                return true;
            }
            if (!process.isAlive()) {
                return false;
            }
            Thread.sleep(250);
        }
        return Files.isRegularFile(log) && Files.readString(log).contains("RIVER_PLATFORM_READY");
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
