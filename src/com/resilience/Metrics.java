package com.resilience;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe counters and latency statistics for one test run. */
public final class Metrics {

    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger success2xx = new AtomicInteger();
    private final AtomicInteger otherStatus = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final AtomicLong minLatencyMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatencyMs = new AtomicLong(0);
    private final Map<Integer, AtomicInteger> statusHistogram = new TreeMap<>();

    public void record(int statusCode, long latencyMs) {
        total.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        minLatencyMs.accumulateAndGet(latencyMs, Math::min);
        maxLatencyMs.accumulateAndGet(latencyMs, Math::max);
        statusHistogram.computeIfAbsent(statusCode, k -> new AtomicInteger()).incrementAndGet();
        if (statusCode >= 200 && statusCode < 300) {
            success2xx.incrementAndGet();
        } else {
            otherStatus.incrementAndGet();
        }
    }

    public void recordError() {
        total.incrementAndGet();
        errors.incrementAndGet();
    }

    public int total() {
        return total.get();
    }

    public String renderReport(double elapsedSeconds) {
        int n = total.get();
        double avg = n == 0 ? 0 : (double) totalLatencyMs.get() / n;
        long min = minLatencyMs.get() == Long.MAX_VALUE ? 0 : minLatencyMs.get();
        StringBuilder out = new StringBuilder();
        out.append("Load Resilience Test -- results\n");
        out.append("================================\n");
        out.append(String.format("Total requests:   %d%n", n));
        out.append(String.format("2xx responses:    %d%n", success2xx.get()));
        out.append(String.format("Other statuses:   %d%n", otherStatus.get()));
        out.append(String.format("Errors:           %d%n", errors.get()));
        out.append(String.format("Elapsed:          %.1fs (achieved %.2f req/s)%n",
                elapsedSeconds, elapsedSeconds > 0 ? n / elapsedSeconds : 0));
        out.append(String.format("Latency avg/min/max: %.1f / %d / %d ms%n", avg, min, maxLatencyMs.get()));
        out.append("Status codes:\n");
        statusHistogram.forEach((code, count) ->
                out.append(String.format("  %d: %d%n", code, count.get())));
        if (errors.get() > 0 || otherStatus.get() > 0) {
            out.append("Note: non-2xx / errors under a tiny capped load usually mean\n");
            out.append("the target rate-limited, rejected, or was unavailable -- that is\n");
            out.append("exactly what this tool is for: finding out before real traffic does.\n");
        }
        return out.toString();
    }
}
