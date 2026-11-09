package com.resilience;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Sends one plain, identifiable HTTP GET at a time.
 *
 * Deliberately boring: a single fixed User-Agent, no proxy, no header
 * rotation, no body, no keep-alive abuse. A defender should be able to
 * spot this traffic trivially -- that is a feature, not a limitation.
 */
public final class LoadClient {

    private final String targetUrl;

    public LoadClient(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    /** Sends one GET. Returns the HTTP status code, or -1 on transport error. */
    public int sendOnce(Metrics metrics) {
        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(targetUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", Config.USER_AGENT);
            connection.setConnectTimeout(Config.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(Config.READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            int status = connection.getResponseCode();
            // Drain the body so the connection closes cleanly and the
            // measurement reflects a complete request/response cycle.
            try {
                if (connection.getInputStream() != null) {
                    connection.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                }
            } catch (IOException ignored) {
                // Error bodies are read the same way by getResponseCode callers;
                // a missing body does not change the recorded status.
            }
            metrics.record(status, System.currentTimeMillis() - start);
            return status;
        } catch (IOException e) {
            metrics.recordError();
            return -1;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
