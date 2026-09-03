package dev.tobifrosch.turnstile.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.tobifrosch.turnstile.gate.GateService;
import dev.tobifrosch.turnstile.messaging.MessageService;
import dev.tobifrosch.turnstile.messaging.MsgOpts;
import dev.tobifrosch.turnstile.messaging.TurnstileMsgKeys;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Replacement for Velocity's built-in {@code /server} command (spec §5): lists and
 * tab-completes only servers {@link GateService} allows for the invoking player, otherwise
 * behaving like the stock command (connect on an exact, allowed match).
 */
public final class ServerCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final GateService gateService;
    private final MessageService messageService;

    public ServerCommand(ProxyServer proxy, GateService gateService, MessageService messageService) {
        this.proxy = proxy;
        this.gateService = gateService;
        this.messageService = messageService;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            List<String> servers = visibleServerNames(source);
            if (servers.isEmpty()) {
                this.messageService.info(source, TurnstileMsgKeys.SERVER_LIST_EMPTY);
            } else {
                this.messageService.info(source, TurnstileMsgKeys.SERVER_LIST,
                    MsgOpts.with("servers", String.join(", ", servers)));
            }
            return;
        }

        String targetName = args[0];
        Optional<RegisteredServer> target = this.proxy.getServer(targetName);
        boolean allowed = target.isPresent()
            && this.gateService.allowed(target.get().getServerInfo().getName(), source::hasPermission);
        if (!allowed) {
            this.messageService.error(source, TurnstileMsgKeys.SERVER_UNKNOWN, MsgOpts.with("server", targetName));
            return;
        }
        if (source instanceof Player player) {
            player.createConnectionRequest(target.get()).fireAndForget();
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 1) {
            return List.of();
        }
        String partial = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        return visibleServerNames(invocation.source()).stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
            .toList();
    }

    private List<String> visibleServerNames(CommandSource source) {
        return this.proxy.getAllServers().stream()
            .map(server -> server.getServerInfo().getName())
            .filter(name -> this.gateService.allowed(name, source::hasPermission))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }
}
