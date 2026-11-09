package com.resilience;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Decides whether a target URL may be tested at all.
 *
 * Allowed targets are deliberately narrow:
 * <ul>
 *   <li>loopback addresses only -- {@code localhost}, {@code 127.0.0.1},
 *       {@code ::1} -- resolved and verified as loopback, not just by name; or</li>
 *   <li>hosts explicitly listed in {@code allowlist.txt}, a file the user
 *       owns and edits by hand, one host per line.</li>
 * </ul>
 * Everything else is refused before a single request is sent. There is no
 * proxy support, no header spoofing, and no way to add a target at runtime
 * other than editing the allowlist file.
 */
public final class Allowlist {

    public static final String ALLOWLIST_FILE = "allowlist.txt";

    private Allowlist() {
    }

    /** Loads the allowlist file if present; an absent file means "loopback only". */
    public static Set<String> load(Path file) throws IOException {
        Set<String> allowed = new HashSet<>();
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file)) {
                String entry = line.strip();
                if (!entry.isEmpty() && !entry.startsWith("#")) {
                    allowed.add(entry.toLowerCase());
                }
            }
        }
        return allowed;
    }

    /** Returns true only for loopback targets or hosts in the allowlist. */
    public static boolean isAllowed(String targetUrl, Set<String> allowlistedHosts) {
        URI uri;
        try {
            uri = URI.create(targetUrl);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        if (allowlistedHosts.contains(host.toLowerCase())) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isLoopbackAddress();
        } catch (IOException e) {
            return false; // unresolvable host: refuse
        }
    }
}
