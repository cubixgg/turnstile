package dev.tobifrosch.turnstile;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.tobifrosch.turnstile.cache.TurnstileCache;
import dev.tobifrosch.turnstile.command.ServerCommand;
import dev.tobifrosch.turnstile.command.TurnstileCommand;
import dev.tobifrosch.turnstile.config.BootstrapConfig;
import dev.tobifrosch.turnstile.db.Database;
import dev.tobifrosch.turnstile.db.SettingsRepository;
import dev.tobifrosch.turnstile.db.TaskPermissionRepository;
import dev.tobifrosch.turnstile.gate.GateService;
import dev.tobifrosch.turnstile.listener.ServerGateListener;
import dev.tobifrosch.turnstile.messaging.LangLoader;
import dev.tobifrosch.turnstile.messaging.MessageService;
import dev.tobifrosch.turnstile.messaging.MessageServiceImpl;
import java.nio.file.Path;
import org.slf4j.Logger;

/**
 * Turnstile — double-secures backend server switches behind a global server-name prefix and
 * per-task permission nodes (see {@code spec.md} for the full design).
 */
@Plugin(
    id = "turnstile",
    name = "Turnstile",
    version = "1.0.0",
    description = "Server-prefix and task-permission gate for backend server switches",
    authors = {"tobifrosch"}
)
public final class TurnstilePlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private Database database;

    @Inject
    public TurnstilePlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        BootstrapConfig config;
        try {
            config = BootstrapConfig.load(this.dataDirectory);
        } catch (Exception e) {
            this.logger.error("Failed to load config.toml — Turnstile disabled", e);
            return;
        }

        this.database = new Database(this.proxy, this, this.logger);
        if (!this.database.connect(config)) {
            this.logger.error("Could not connect to PostgreSQL — Turnstile disabled "
                + "(server gating is inactive, the stock /server command stays active)");
            this.database = null;
            return;
        }

        MessageService messageService = new MessageServiceImpl(LangLoader.load(this.logger));

        TaskPermissionRepository taskPermissionRepository = new TaskPermissionRepository(this.database, this.logger);
        SettingsRepository settingsRepository = new SettingsRepository(this.database, this.logger);

        TurnstileCache cache = new TurnstileCache();
        cache.loadInitial(taskPermissionRepository.loadAll(), settingsRepository.loadServerPrefix().orElse(null));

        GateService gateService = new GateService(cache);

        this.proxy.getEventManager().register(this, new ServerGateListener(gateService, messageService));

        this.proxy.getCommandManager().unregister("server");
        CommandMeta serverMeta = this.proxy.getCommandManager().metaBuilder("server").plugin(this).build();
        this.proxy.getCommandManager().register(serverMeta,
            new ServerCommand(this.proxy, gateService, messageService));

        CommandMeta turnstileMeta = this.proxy.getCommandManager().metaBuilder("turnstile").plugin(this).build();
        this.proxy.getCommandManager().register(turnstileMeta,
            new TurnstileCommand(this.database, cache, taskPermissionRepository, settingsRepository, messageService));

        String prefixLog = cache.serverPrefix() == null ? "<none>" : cache.serverPrefix();
        this.logger.info("Turnstile initialized — prefix \"{}\", {} task permission(s) loaded",
            prefixLog, cache.allTaskPermissions().size());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.database != null) {
            this.database.close();
        }
    }
}
