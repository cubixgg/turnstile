package dev.tobifrosch.turnstile.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Local, non-tunable bootstrap configuration ({@code config.toml} in the plugin data
 * directory) — the database connection only. Everything else (task permissions, the server
 * prefix) lives in Postgres and is managed via {@code /turnstile}.
 *
 * <p>Parses just the fixed, flat {@code [database]} table this file uses (5 string/int keys,
 * no arrays, no nested tables, no dates) with a small hand-rolled reader instead of pulling in
 * a full TOML library and shading it into the plugin jar.
 */
public final class BootstrapConfig {

    private static final String DEFAULT_CONTENT = """
        [database]
        host = "localhost"
        port = 5432
        database = "velocity_taskgate"
        user = "postgres"
        password = "changeme"
        """;

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    private BootstrapConfig(Map<String, String> values) {
        this.host = values.getOrDefault("host", "localhost");
        this.port = Integer.parseInt(values.getOrDefault("port", "5432"));
        this.database = values.getOrDefault("database", "velocity_taskgate");
        this.user = values.getOrDefault("user", "postgres");
        this.password = values.getOrDefault("password", "changeme");
    }

    public static BootstrapConfig load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve("config.toml");
        if (!Files.exists(file)) {
            Files.writeString(file, DEFAULT_CONTENT);
        }
        return new BootstrapConfig(parseDatabaseTable(file));
    }

    private static Map<String, String> parseDatabaseTable(Path file) throws IOException {
        Map<String, String> values = new HashMap<>();
        boolean inDatabaseSection = false;
        for (String rawLine : Files.readAllLines(file)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                inDatabaseSection = line.equalsIgnoreCase("[database]");
                continue;
            }
            if (!inDatabaseSection) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = stripQuotes(line.substring(eq + 1).strip());
            values.put(key, value);
        }
        return values;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + this.host + ":" + this.port + "/" + this.database;
    }

    public String user() {
        return this.user;
    }

    public String password() {
        return this.password;
    }
}
