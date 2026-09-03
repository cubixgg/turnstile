package dev.tobifrosch.turnstile.gate;

import dev.tobifrosch.turnstile.cache.TurnstileCache;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The core check from spec §3: a server switch is allowed only if (a) no server prefix is
 * configured, or the target server name starts with it, and (b) no configured task matches the
 * target server name, or the player holds that task's permission node.
 *
 * <p>Pure logic over a {@link TurnstileCache} and a caller-supplied permission check — no
 * Velocity types here, so it can be unit-tested directly and is reused as-is by both the
 * {@code ServerPreConnectEvent} listener and the {@code /server} visibility filter.
 */
public final class GateService {

    private final TurnstileCache cache;

    public GateService(TurnstileCache cache) {
        this.cache = cache;
    }

    public enum GateResult {
        ALLOWED,
        DENIED_PREFIX,
        DENIED_PERMISSION
    }

    public boolean prefixAllowed(String serverName) {
        String prefix = this.cache.serverPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return true;
        }
        return TurnstileCache.normalize(serverName).startsWith(TurnstileCache.normalize(prefix));
    }

    /**
     * The task whose name is the longest case-insensitive {@code startsWith} match against
     * {@code serverName}. Longest match wins so that overlapping task names (e.g. {@code build}
     * and {@code building}) resolve deterministically to the more specific one, regardless of
     * map iteration order.
     */
    public Optional<String> matchingPermission(String serverName) {
        String lowerServer = TurnstileCache.normalize(serverName);
        String bestTask = null;
        String bestPermission = null;
        for (Map.Entry<String, String> entry : this.cache.allTaskPermissions().entrySet()) {
            String lowerTask = TurnstileCache.normalize(entry.getKey());
            if (lowerServer.startsWith(lowerTask) && (bestTask == null || lowerTask.length() > bestTask.length())) {
                bestTask = lowerTask;
                bestPermission = entry.getValue();
            }
        }
        return Optional.ofNullable(bestPermission);
    }

    public GateResult check(String serverName, Predicate<String> hasPermission) {
        if (!prefixAllowed(serverName)) {
            return GateResult.DENIED_PREFIX;
        }
        Optional<String> requiredPermission = matchingPermission(serverName);
        if (requiredPermission.isPresent() && !hasPermission.test(requiredPermission.get())) {
            return GateResult.DENIED_PERMISSION;
        }
        return GateResult.ALLOWED;
    }

    public boolean allowed(String serverName, Predicate<String> hasPermission) {
        return check(serverName, hasPermission) == GateResult.ALLOWED;
    }
}
