package dev.tobifrosch.turnstile.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;

/** Persistence for the {@code task_permissions} table (spec §6). */
public final class TaskPermissionRepository {

    private final Database database;
    private final Logger logger;

    public TaskPermissionRepository(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /** Synchronous — only ever called once at startup, before the cache exists. */
    public Map<String, String> loadAll() {
        Map<String, String> result = new LinkedHashMap<>();
        if (!this.database.isAvailable()) {
            return result;
        }
        try (Connection connection = this.database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT task_name, permission_node FROM task_permissions")) {
            while (rs.next()) {
                result.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            this.logger.error("Failed to load task permissions from PostgreSQL", e);
        }
        return result;
    }

    public void upsertAsync(String taskName, String permissionNode) {
        this.database.runAsync(() -> {
            try (Connection connection = this.database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO task_permissions (task_name, permission_node, updated_at) VALUES (?, ?, now())
                     ON CONFLICT (task_name) DO UPDATE SET permission_node = EXCLUDED.permission_node, updated_at = now()""")) {
                statement.setString(1, taskName);
                statement.setString(2, permissionNode);
                statement.executeUpdate();
            } catch (SQLException e) {
                this.logger.error("Failed to persist task permission {}={}", taskName, permissionNode, e);
            }
        });
    }

    public void deleteAsync(String taskName) {
        this.database.runAsync(() -> {
            try (Connection connection = this.database.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM task_permissions WHERE task_name = ?")) {
                statement.setString(1, taskName);
                statement.executeUpdate();
            } catch (SQLException e) {
                this.logger.error("Failed to delete task permission {}", taskName, e);
            }
        });
    }
}
