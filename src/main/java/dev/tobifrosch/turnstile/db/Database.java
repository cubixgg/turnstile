package dev.tobifrosch.turnstile.db;

import com.velocitypowered.api.proxy.ProxyServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.tobifrosch.turnstile.config.BootstrapConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;

/**
 * Postgres access: connection pool, schema bootstrap, and the async write path. Reads used on
 * the hot path (gate checks, {@code /server} filtering) never touch this class directly — they
 * go through the in-memory cache, which is loaded from here once at startup.
 *
 * <p>Writes are scheduled on Velocity's own proxy scheduler ({@link #runAsync}), not a
 * hand-rolled executor, per spec §7 — that keeps them off the Netty event loop without the
 * plugin managing its own thread pool.
 */
public final class Database implements AutoCloseable {

    private final ProxyServer proxy;
    private final Object plugin;
    private final Logger logger;
    private HikariDataSource dataSource;
    private volatile boolean available;

    public Database(ProxyServer proxy, Object plugin, Logger logger) {
        this.proxy = proxy;
        this.plugin = plugin;
        this.logger = logger;
    }

    public boolean connect(BootstrapConfig config) {
        try {
            HikariConfig hikari = new HikariConfig();
            hikari.setPoolName("turnstile");
            hikari.setDataSourceClassName(PGSimpleDataSource.class.getName());
            hikari.addDataSourceProperty("url", config.jdbcUrl());
            hikari.setUsername(config.user());
            hikari.setPassword(config.password());
            hikari.setMaximumPoolSize(4);
            hikari.setMinimumIdle(1);
            hikari.setInitializationFailTimeout(5000);
            this.dataSource = new HikariDataSource(hikari);
            initSchema();
            this.available = true;
            return true;
        } catch (Exception e) {
            this.available = false;
            this.logger.error("Could not connect to PostgreSQL", e);
            return false;
        }
    }

    public boolean isAvailable() {
        return this.available;
    }

    public Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();
    }

    private void initSchema() throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS task_permissions (
                    task_name       TEXT PRIMARY KEY,
                    permission_node TEXT NOT NULL,
                    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
                )""");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS plugin_settings (
                    key   TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )""");
        }
    }

    /** Runs {@code task} on Velocity's proxy scheduler, off the Netty event loop. */
    public void runAsync(Runnable task) {
        this.proxy.getScheduler().buildTask(this.plugin, task).schedule();
    }

    @Override
    public void close() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }
}
