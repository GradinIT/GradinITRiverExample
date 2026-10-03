package se.gradinit.riverexample.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Starts GradinITRiver from {@code gradinit-river-dist} ({@code bin/river-platform --clean}),
 * deploys customer and order with {@code bin/river}, calls the client, checks HRW routing and
 * failover onto another backend, then undeploys.
 */
class OrderPlatformIT {
    private static final Pattern READY = Pattern.compile("RIVER_PLATFORM_READY\\s+(jini://\\S+)");

    private Process platform;

    @AfterEach
    void stopPlatform() {
        destroyTree(platform);
        platform = null;
    }

    @Test
    @Timeout(value = 12, unit = TimeUnit.MINUTES)
    void deployCallMonitorUndeploy() throws Exception {
        Path repoRoot = Path.of(System.getProperty("user.dir")).getParent();
        if (!Files.isDirectory(repoRoot.resolve("client"))) {
            repoRoot = Path.of(System.getProperty("user.dir"));
        }
        Path distHome = distHome();
        Path platformBin = distHome.resolve("bin/river-platform");
        Path riverBin = distHome.resolve("bin/river");
        Path consoleBin = distHome.resolve("bin/river-web-console");
        assertTrue(Files.isRegularFile(platformBin) || Files.isSymbolicLink(platformBin), "saknar " + platformBin);
        assertTrue(Files.isRegularFile(riverBin) || Files.isSymbolicLink(riverBin), "saknar " + riverBin);
        assertTrue(Files.isRegularFile(consoleBin) || Files.isSymbolicLink(consoleBin), "saknar " + consoleBin);

        Path customerJar = repoRoot.resolve("customer-component/target/customer-component-1.0.0.jar");
        Path orderJar = repoRoot.resolve("order-component/target/order-component-1.0.0.jar");
        Path clientJar = repoRoot.resolve("client/target/client-1.0.0.jar");
        Path classpathFile = repoRoot.resolve("client/target/classpath.txt");
        String compatProperty = System.getProperty("river.compat.jar", "");
        assertFalse(compatProperty.isBlank(), "saknar system property river.compat.jar");
        Path compatJar = Path.of(compatProperty);
        assertTrue(Files.isRegularFile(customerJar), "saknar " + customerJar);
        assertTrue(Files.isRegularFile(orderJar), "saknar " + orderJar);
        assertTrue(Files.isRegularFile(clientJar), "saknar " + clientJar);
        assertTrue(Files.isRegularFile(classpathFile), "saknar " + classpathFile);
        assertTrue(Files.isRegularFile(compatJar), "saknar " + compatJar);

        Path logs = repoRoot.resolve("integration-tests/target/platform-logs");
        Files.createDirectories(logs);
        Path platformLog = logs.resolve("platform.log");
        platform = start(platformLog, distHome, Map.of(), command(platformBin, "--clean"));
        String locator = awaitReady(platform, platformLog, Duration.ofSeconds(120));
        assertNotNull(locator, "plattformen blev inte RIVER_PLATFORM_READY\n" + read(platformLog)
                + "\n--- bin/river-platform ---\n" + scriptHead(platformBin));
        locator = usableLocator(locator);

        Map<String, String> lookupEnv = Map.of(
                "JAVA_TOOL_OPTIONS", "-Dse.gradinit.river.lookup=" + locator);

        CommandResult deployedCustomer = river(logs, distHome, riverBin, lookupEnv, "deploy", customerJar.toString());
        assertEquals(0, deployedCustomer.exit, deployedCustomer.output + diagnostics(platform, platformLog, distHome));
        CommandResult listed = river(logs, distHome, riverBin, lookupEnv, "list");
        CommandResult deployedOrder = river(logs, distHome, riverBin, lookupEnv, "deploy", orderJar.toString());
        assertEquals(0, deployedOrder.exit, deployedOrder.output
                + "\n--- customer deploy ---\n" + deployedCustomer.output
                + "\n--- river list ---\n" + listed.output
                + diagnostics(platform, platformLog, distHome));

        CommandResult monitor = monitor(logs, distHome, riverBin, lookupEnv);
        assertTrue(monitor.exit == 0 || !monitor.output.isBlank(), monitor.output);
        assertFalse(monitor.output.isBlank(), "river monitor gav tom utdata");

        List<String> jvm = jvmFlags(compatJar);
        String clientClasspath = clientJar + System.getProperty("path.separator") + Files.readString(classpathFile).trim();
        CommandResult call = client(logs.resolve("client.log"), repoRoot, jvm, locator, clientClasspath);
        assertEquals(0, call.exit, call.output);
        String routedBackend = sameBackend(call.output);

        List<ProcessHandle> backends = backendProcesses(platform);
        assertTrue(backends.size() >= 2, "behöver minst två order-backend för failover, hittade " + backends.size()
                + "\n" + describeProcesses(platform)
                + "\n" + describeHandles(backends));
        ProcessHandle selected = matchBackend(backends, routedBackend);
        ProcessHandle victim = selected != null ? selected : backends.get(0);
        long victimPid = victim.pid();
        victim.destroy();
        if (!victim.onExit().isDone()) {
            Thread.sleep(1000);
        }
        if (victim.isAlive()) {
            victim.destroyForcibly();
        }
        for (int i = 0; i < 20 && victim.isAlive(); i++) {
            Thread.sleep(100);
        }

        assertFalse(victim.isAlive(), "kunde inte stoppa backend-processen " + victimPid
                + " (" + commandLine(victim) + ")");
        CommandResult after = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            after = client(logs.resolve("client-failover-" + attempt + ".log"), repoRoot, jvm, locator, clientClasspath, false);
            if (after.exit == 0 && after.output.contains("ORDER_OK ")) {
                break;
            }
            Thread.sleep(3000);
        }
        assertNotNull(after);
        assertEquals(0, after.exit, "failover efter stoppad backend " + victimPid
                + " (routad backendId=" + routedBackend + ")\n" + after.output);
        assertTrue(after.output.contains("ORDER_OK "), after.output);

        CommandResult undeployOrder = river(logs, distHome, riverBin, lookupEnv, "undeploy", "order");
        if (undeployOrder.exit != 0) {
            undeployOrder = river(logs, distHome, riverBin, lookupEnv, "undeploy", orderJar.toString());
        }
        assertEquals(0, undeployOrder.exit, undeployOrder.output);
        CommandResult undeployCustomer = river(logs, distHome, riverBin, lookupEnv, "undeploy", "customer");
        if (undeployCustomer.exit != 0) {
            undeployCustomer = river(logs, distHome, riverBin, lookupEnv, "undeploy", customerJar.toString());
        }
        assertEquals(0, undeployCustomer.exit, undeployCustomer.output);
    }

    private static Path distHome() throws IOException {
        String file = System.getProperty("river.dist.home.file", "target/river-dist-home.txt");
        Path path = Path.of(file);
        assertTrue(Files.isRegularFile(path), "saknar " + path.toAbsolutePath()
                + " — ./mvnw -B -U package packar upp gradinit-river-dist");
        Path home = Path.of(Files.readString(path).trim());
        assertTrue(Files.isDirectory(home), "saknar distributionskatalog " + home);
        return home;
    }

    private static List<String> command(Path script, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        if (!Files.isExecutable(script)) {
            command.add("bash");
        }
        command.add(script.toString());
        command.addAll(List.of(args));
        return command;
    }

    private static String sameBackend(String output) {
        List<String> ok = output.lines().filter(line -> line.startsWith("ORDER_OK ")).toList();
        assertEquals(2, ok.size(), output);
        String first = ok.get(0).replaceAll(".*backendId=", "");
        String second = ok.get(1).replaceAll(".*backendId=", "");
        assertFalse(first.isBlank(), output);
        assertEquals(first, second, output);
        String firstOrder = ok.get(0).replaceAll(".*orderId=", "").replaceAll("\\s.*", "");
        String secondOrder = ok.get(1).replaceAll(".*orderId=", "").replaceAll("\\s.*", "");
        assertFalse(firstOrder.equals(secondOrder), output);
        return first;
    }

    private static String awaitReady(Process process, Path log, Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            String locator = readyLocator(log);
            if (locator != null) {
                return locator;
            }
            if (!process.isAlive()) {
                return null;
            }
            Thread.sleep(250);
        }
        return readyLocator(log);
    }

    private static String readyLocator(Path log) throws IOException {
        if (!Files.isRegularFile(log)) {
            return null;
        }
        Matcher matcher = READY.matcher(Files.readString(log));
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    static String usableLocator(String raw) {
        String locator = raw.trim();
        while (locator.endsWith(".") || locator.endsWith(",")) {
            locator = locator.substring(0, locator.length() - 1);
        }
        int scheme = locator.indexOf("://");
        if (scheme < 0) {
            return locator;
        }
        String rest = locator.substring(scheme + 3);
        int slash = rest.indexOf('/');
        String hostPort = slash < 0 ? rest : rest.substring(0, slash);
        String tail = slash < 0 ? "" : rest.substring(slash);
        String host = hostPort;
        String port = "";
        if (!hostPort.startsWith("[")) {
            int colon = hostPort.lastIndexOf(':');
            if (colon > 0) {
                host = hostPort.substring(0, colon);
                port = hostPort.substring(colon);
            }
        }
        if (host.equals("0.0.0.0") || host.equals("*") || host.equals("::") || host.equals("[::]")) {
            host = "127.0.0.1";
        }
        return locator.substring(0, scheme) + "://" + host + port + tail;
    }

    private static List<String> jvmFlags(Path compatJar) {
        return List.of(
                "--patch-module", "java.rmi=" + compatJar,
                "--add-exports", "java.rmi/java.rmi.activation=ALL-UNNAMED");
    }

    private static Process start(Path log, Path work, Map<String, String> env, List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(work.toFile());
        builder.environment().putAll(env);
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        return builder.start();
    }

    private static CommandResult river(Path logs, Path work, Path riverBin, Map<String, String> env, String... args)
            throws Exception {
        Path log = logs.resolve(String.join("-", args).replaceAll("[^a-zA-Z0-9._-]", "_") + ".log");
        return run(log, work, env, Duration.ofMinutes(2), command(riverBin, args));
    }

    private static CommandResult monitor(Path logs, Path work, Path riverBin, Map<String, String> env) throws Exception {
        Path log = logs.resolve("monitor.log");
        List<String> command = command(riverBin, "monitor");
        Process process = start(log, work, env, command);
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(log) && !Files.readString(log).isBlank()) {
                break;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(250);
        }
        if (process.isAlive()) {
            process.destroy();
            process.waitFor(5, TimeUnit.SECONDS);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            return new CommandResult(0, read(log));
        }
        return new CommandResult(process.exitValue(), read(log));
    }

    private static CommandResult client(Path log, Path work, List<String> jvm, String locator, String classpath)
            throws Exception {
        return client(log, work, jvm, locator, classpath, true);
    }

    private static CommandResult client(Path log, Path work, List<String> jvm, String locator, String classpath,
            boolean expectStableRoute) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvm);
        command.add("-Dse.gradinit.river.lookup=" + locator);
        command.add("-Driver.example.expectStableRoute=" + expectStableRoute);
        command.add("-cp");
        command.add(classpath);
        command.add("se.gradinit.riverexample.client.OrderClient");
        command.add("alice");
        command.add("SKU-100");
        command.add("1");
        return run(log, work, Map.of(), Duration.ofMinutes(2), command);
    }

    private static CommandResult run(Path log, Path work, Map<String, String> env, Duration timeout, List<String> command)
            throws Exception {
        Process process = start(log, work, env, command);
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            destroyTree(process);
            return new CommandResult(-1, read(log) + "\nTIMEOUT");
        }
        return new CommandResult(process.exitValue(), read(log));
    }

    private static List<ProcessHandle> backendProcesses(Process platform) {
        List<ProcessHandle> found = new ArrayList<>();
        for (ProcessHandle handle : platform.toHandle().descendants().toList()) {
            if (commandLine(handle).contains("OrderBackendMain")) {
                found.add(handle);
            }
        }
        if (found.size() >= 2) {
            return found;
        }
        for (ProcessHandle handle : ProcessHandle.allProcesses().toList()) {
            if (commandLine(handle).contains("OrderBackendMain") && !containsPid(found, handle.pid())) {
                found.add(handle);
            }
        }
        return found;
    }

    private static boolean containsPid(List<ProcessHandle> handles, long pid) {
        for (ProcessHandle handle : handles) {
            if (handle.pid() == pid) {
                return true;
            }
        }
        return false;
    }

    private static ProcessHandle matchBackend(List<ProcessHandle> backends, String backendId) throws IOException {
        for (ProcessHandle handle : backends) {
            if (Long.toString(handle.pid()).equals(backendId)) {
                return handle;
            }
            String command = commandLine(handle);
            String env = environ(handle.pid());
            if (command.contains("river.instance=" + backendId) || env.contains("river.instance=" + backendId)) {
                return handle;
            }
        }
        return null;
    }

    private static String commandLine(ProcessHandle handle) {
        try {
            String fromApi = handle.info().commandLine().orElse("");
            if (!fromApi.isBlank()) {
                return fromApi;
            }
            Path cmdline = Path.of("/proc", Long.toString(handle.pid()), "cmdline");
            if (!Files.isRegularFile(cmdline)) {
                return handle.info().command().orElse("");
            }
            return new String(Files.readAllBytes(cmdline)).replace('\0', ' ');
        } catch (IOException | SecurityException e) {
            return "";
        }
    }

    private static String environ(long pid) throws IOException {
        Path path = Path.of("/proc", Long.toString(pid), "environ");
        if (!Files.isRegularFile(path)) {
            return "";
        }
        return new String(Files.readAllBytes(path)).replace('\0', '\n');
    }

    private static String describeProcesses(Process platform) {
        StringBuilder text = new StringBuilder();
        for (ProcessHandle handle : platform.toHandle().descendants().toList()) {
            text.append(handle.pid()).append(' ').append(commandLine(handle)).append('\n');
        }
        return text.toString();
    }

    private static String describeHandles(List<ProcessHandle> handles) {
        StringBuilder text = new StringBuilder();
        for (ProcessHandle handle : handles) {
            text.append(handle.pid()).append(' ').append(commandLine(handle)).append('\n');
        }
        return text.toString();
    }

    private static String scriptHead(Path script) {
        try {
            if (!Files.isRegularFile(script)) {
                return "";
            }
            List<String> lines = Files.readAllLines(script);
            int end = Math.min(lines.size(), 80);
            return String.join("\n", lines.subList(0, end));
        } catch (IOException | java.io.UncheckedIOException e) {
            return e.toString();
        }
    }

    private static String read(Path log) throws IOException {
        return Files.isRegularFile(log) ? Files.readString(log) : "";
    }

    private static String diagnostics(Process platformProcess, Path platformLog, Path distHome) throws IOException {
        String log = read(platformLog);
        if (log.length() > 8000) {
            log = log.substring(log.length() - 8000);
        }
        String env = read(distHome.resolve("bin/_river-env.sh"));
        if (env.length() > 4000) {
            env = env.substring(0, 4000);
        }
        return "\n--- platform ---\n" + log
                + "\n--- _river-env.sh ---\n" + env
                + "\n--- processes ---\n" + describeProcesses(platformProcess);
    }

    private static void destroyTree(Process process) {
        if (process == null) {
            return;
        }
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(ProcessHandle::destroyForcibly);
        handle.destroyForcibly();
    }

    private record CommandResult(int exit, String output) {}
}
