package com.resilience;

import java.time.Duration;

/**
 * Immutable run configuration for the load-resilience tester.
 *
 * Hard safety caps are enforced here, in one place, so no caller can
 * raise them: at most {@value #MAX_REQUESTS_PER_SECOND} requests/second,
 * at most {@value #MAX_DURATION_SECONDS} seconds per run, and at most
 * {@value #MAX_TOTAL_REQUESTS} requests in total. There is no way to
 * configure a higher rate -- that is the point of this tool.
 */
public final class Config {

    public static final int MAX_REQUESTS_PER_SECOND = 10;
    public static final int MAX_DURATION_SECONDS = 60;
    public static final int MAX_TOTAL_REQUESTS = 600;
    public static final int DEFAULT_REQUESTS_PER_SECOND = 5;
    public static final int DEFAULT_DURATION_SECONDS = 10;
    public static final int CONNECT_TIMEOUT_MS = 2_000;
    public static final int READ_TIMEOUT_MS = 5_000;
    public static final String USER_AGENT = "load-resilience-tester/1.0 (authorized local test)";

    private final String targetUrl;
    private final int requestsPerSecond;
    private final int durationSeconds;

    private Config(String targetUrl, int requestsPerSecond, int durationSeconds) {
        this.targetUrl = targetUrl;
        this.requestsPerSecond = requestsPerSecond;
        this.durationSeconds = durationSeconds;
    }

    public static Config parse(String[] args) {
        String target = null;
        int rps = DEFAULT_REQUESTS_PER_SECOND;
        int duration = DEFAULT_DURATION_SECONDS;
        boolean authorize = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--target" -> target = requireValue(args, ++i, "--target");
                case "--rps" -> rps = Integer.parseInt(requireValue(args, ++i, "--rps"));
                case "--duration" -> duration = Integer.parseInt(requireValue(args, ++i, "--duration"));
                case "--authorize" -> authorize = true;
                case "--help", "-h" -> {
                    Main.printHelp();
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (!authorize) {
            throw new IllegalArgumentException(
                    "Refusing to run without --authorize. This tool only tests servers you own.");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Missing required --target <url>.");
        }
        if (rps < 1 || rps > MAX_REQUESTS_PER_SECOND) {
            throw new IllegalArgumentException(
                    "Request rate must be between 1 and " + MAX_REQUESTS_PER_SECOND + " req/s.");
        }
        if (duration < 1 || duration > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException(
                    "Duration must be between 1 and " + MAX_DURATION_SECONDS + " seconds.");
        }
        if ((long) rps * duration > MAX_TOTAL_REQUESTS) {
            throw new IllegalArgumentException(
                    "Run would exceed the cap of " + MAX_TOTAL_REQUESTS + " total requests.");
        }
        return new Config(target, rps, duration);
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[index];
    }

    public String targetUrl() {
        return targetUrl;
    }

    public int requestsPerSecond() {
        return requestsPerSecond;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public int totalPlannedRequests() {
        return requestsPerSecond * durationSeconds;
    }

    public Duration intervalBetweenRequests() {
        return Duration.ofMillis(1000L / requestsPerSecond);
    }
}
