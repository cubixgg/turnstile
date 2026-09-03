package dev.tobifrosch.turnstile.cache;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, write-through cache of task->permission mappings and the server prefix (spec §7).
 * Loaded once from Postgres at startup; every command mutation updates this cache synchronously
 * so the hot path ({@code ServerPreConnectEvent}, {@code /server} filtering) never touches the
 * database. Task names keep their original casing (matching is case-insensitive at lookup time
 * only — see {@link dev.tobifrosch.turnstile.gate.GateService}).
 */
public final class TurnstileCache {

    private final Map<String, String> taskPermissions = new ConcurrentHashMap<>();
    private volatile String serverPrefix;

    public void loadInitial(Map<String, String> tasks, String prefix) {
        this.taskPermissions.putAll(tasks);
        this.serverPrefix = prefix;
    }

    public void putTaskPermission(String taskName, String permissionNode) {
        removeIgnoreCase(taskName);
        this.taskPermissions.put(taskName, permissionNode);
    }

    /** @return {@code true} if a mapping (matched case-insensitively) existed and was removed. */
    public boolean removeTaskPermission(String taskName) {
        return removeIgnoreCase(taskName);
    }

    private boolean removeIgnoreCase(String taskName) {
        for (String existing : this.taskPermissions.keySet()) {
            if (existing.equalsIgnoreCase(taskName)) {
                return this.taskPermissions.remove(existing) != null;
            }
        }
        return false;
    }

    public Map<String, String> allTaskPermissions() {
        return Map.copyOf(this.taskPermissions);
    }

    /** {@code null} means no prefix is configured — the prefix check is then skipped entirely. */
    public String serverPrefix() {
        return this.serverPrefix;
    }

    public void setServerPrefix(String prefix) {
        this.serverPrefix = prefix;
    }

    public static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
