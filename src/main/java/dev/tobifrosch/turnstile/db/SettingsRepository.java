package dev.tobifrosch.turnstile.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * Persistence for the {@code plugin_settings} key/value table (spec §6). The server prefix is
 * stored as a single row keyed {@code "server_prefix"}.
 */
public final class SettingsRepository {

    private static final String SERVER_PREFIX_KEY = "server_prefix";

    private final Database database;
    private final Logger logger;

    public SettingsRepository(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /** Synchronous — only ever called once at startup, before the cache exists. */
    public Optional<String> loadServerPrefix() {
        if (!this.database.isAvailable()) {
            return Optional.empty();
        }
        try (Connection connection = this.database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT value FROM plugin_settings WHERE key = ?")) {
            statement.setString(1, SERVER_PREFIX_KEY);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            this.logger.error("Failed to load server prefix from PostgreSQL", e);
            return Optional.empty();
        }
    }

    public void saveServerPrefixAsync(String prefix) {
        this.database.runAsync(() -> {
            try (Connection connection = this.database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO plugin_settings (key, value) VALUES (?, ?)
                     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value""")) {
                statement.setString(1, SERVER_PREFIX_KEY);
                statement.setString(2, prefix);
                statement.executeUpdate();
            } catch (SQLException e) {
                this.logger.error("Failed to persist server prefix {}", prefix, e);
            }
        });
    }
}
