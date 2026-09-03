package dev.tobifrosch.turnstile.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.tobifrosch.turnstile.gate.GateService;
import dev.tobifrosch.turnstile.messaging.MessageService;
import dev.tobifrosch.turnstile.messaging.MsgOpts;
import dev.tobifrosch.turnstile.messaging.TurnstileMsgKeys;

/**
 * Enforces spec §3 on every server-switch attempt (explicit {@code /server} and the initial
 * connection try-list both fire {@link ServerPreConnectEvent}).
 */
public final class ServerGateListener {

    private final GateService gateService;
    private final MessageService messageService;

    public ServerGateListener(GateService gateService, MessageService messageService) {
        this.gateService = gateService;
        this.messageService = messageService;
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        RegisteredServer target = event.getOriginalServer();
        String serverName = target.getServerInfo().getName();
        Player player = event.getPlayer();

        GateService.GateResult result = this.gateService.check(serverName, player::hasPermission);
        switch (result) {
            case DENIED_PREFIX -> {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                this.messageService.error(player, TurnstileMsgKeys.GATE_DENIED_PREFIX, MsgOpts.empty());
            }
            case DENIED_PERMISSION -> {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                this.messageService.error(player, TurnstileMsgKeys.GATE_DENIED_PERMISSION, MsgOpts.empty());
            }
            case ALLOWED -> {
                // no-op, let the connection proceed
            }
        }
    }
}
