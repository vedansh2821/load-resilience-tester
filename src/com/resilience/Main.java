package com.resilience;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/**
 * Entry point. Flow:
 * <ol>
 *   <li>Parse and cap the config (see {@link Config}).</li>
 *   <li>Refuse any target that is not loopback or allowlisted.</li>
 *   <li>Require interactive confirmation -- the operator must type YES.</li>
 *   <li>Send capped, rate-limited GETs and print a metrics report.</li>
 * </ol>
 * Run it against the bundled demo server, never against a service you
 * do not own or have written permission to test.
 */
public final class Main {

    private Main() {
    }

    public static void printHelp() {
        System.out.println("load-resilience-tester -- test YOUR OWN server's rate-limit / resilience");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java com.resilience.Main --target <url> --authorize [--rps N] [--duration S]");
        System.out.println();
        System.out.println("  --target    URL to test. Loopback (localhost / 127.0.0.1 / ::1) only,");
        System.out.println("              or a host listed in allowlist.txt (one host per line).");
        System.out.println("  --authorize Required. Confirms you own the target or have written");
        System.out.println("              permission to test it. You will also be asked to type YES.");
        System.out.println("  --rps       Requests per second, 1-10 (default 5).");
        System.out.println("  --duration  Seconds, 1-60 (default 10). Total requests capped at 600.");
        System.out.println();
        System.out.println("Demo (in another terminal, start the test server first):");
        System.out.println("  python3 demo/server.py");
        System.out.println("  java com.resilience.Main --target http://127.0.0.1:8765/ --authorize");
    }

    public static void main(String[] args) {
        Config config;
        try {
            config = Config.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Run with --help for usage.");
            System.exit(2);
            return;
        }

        Set<String> allowlisted;
        try {
            allowlisted = Allowlist.load(Path.of(Allowlist.ALLOWLIST_FILE));
        } catch (IOException e) {
            System.err.println("Error: could not read " + Allowlist.ALLOWLIST_FILE + ": " + e.getMessage());
            System.exit(2);
            return;
        }
        if (!Allowlist.isAllowed(config.targetUrl(), allowlisted)) {
            System.err.println("Refused: target is not loopback and is not in " + Allowlist.ALLOWLIST_FILE);
            System.err.println("This tool only tests localhost or hosts you explicitly allowlist.");
            System.exit(2);
            return;
        }

        System.out.println("Target: " + config.targetUrl());
        System.out.println("Planned: " + config.totalPlannedRequests() + " requests at "
                + config.requestsPerSecond() + " req/s for " + config.durationSeconds() + "s");
        System.out.print("You confirm you own this target or have written permission to test it. Type YES to continue: ");
        System.out.flush();
        String answer = "";
        try {
            answer = new BufferedReader(new InputStreamReader(System.in)).readLine();
        } catch (IOException e) {
            System.err.println("Error reading confirmation.");
            System.exit(2);
            return;
        }
        if (!"YES".equals(answer == null ? null : answer.strip())) {
            System.out.println("Aborted: confirmation not given.");
            System.exit(1);
            return;
        }

        Metrics metrics = new Metrics();
        LoadClient client = new LoadClient(config.targetUrl());
        long startNanos = System.nanoTime();
        Duration interval = config.intervalBetweenRequests();
        for (int i = 0; i < config.totalPlannedRequests(); i++) {
            long tickStart = System.nanoTime();
            client.sendOnce(metrics);
            long elapsed = System.nanoTime() - tickStart;
            long sleepNanos = interval.toNanos() - elapsed;
            if (sleepNanos > 0 && i < config.totalPlannedRequests() - 1) {
                try {
                    Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        System.out.println();
        System.out.print(metrics.renderReport(elapsedSeconds));
    }
}
