package dev.tobifrosch.turnstile.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import dev.tobifrosch.turnstile.cache.TurnstileCache;
import dev.tobifrosch.turnstile.db.Database;
import dev.tobifrosch.turnstile.db.SettingsRepository;
import dev.tobifrosch.turnstile.db.TaskPermissionRepository;
import dev.tobifrosch.turnstile.messaging.MessageService;
import dev.tobifrosch.turnstile.messaging.MsgOpts;
import dev.tobifrosch.turnstile.messaging.TurnstileMsgKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /turnstile permission set|remove|list [page]} and
 * {@code /turnstile server_prefix set|show} (spec §4), gated by the hardwired
 * {@code turnstile.admin} permission (spec §9).
 */
public final class TurnstileCommand implements SimpleCommand {

    public static final String PERMISSION_ADMIN = "turnstile.admin";
    private static final int LIST_PAGE_SIZE = 8;

    private final Database database;
    private final TurnstileCache cache;
    private final TaskPermissionRepository taskPermissionRepository;
    private final SettingsRepository settingsRepository;
    private final MessageService messageService;

    public TurnstileCommand(Database database, TurnstileCache cache, TaskPermissionRepository taskPermissionRepository,
                            SettingsRepository settingsRepository, MessageService messageService) {
        this.database = database;
        this.cache = cache;
        this.taskPermissionRepository = taskPermissionRepository;
        this.settingsRepository = settingsRepository;
        this.messageService = messageService;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION_ADMIN);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission(PERMISSION_ADMIN)) {
            this.messageService.error(source, TurnstileMsgKeys.COMMON_NO_PERMISSION);
            return;
        }
        String[] args = invocation.arguments();
        if (args.length == 0) {
            usage(source);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "permission" -> handlePermission(source, args);
            case "server_prefix" -> handleServerPrefix(source, args);
            default -> usage(source);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission(PERMISSION_ADMIN)) {
            return List.of();
        }
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return List.of("permission", "server_prefix");
        }
        if (args[0].equalsIgnoreCase("permission") && args.length == 2) {
            return List.of("set", "remove", "list");
        }
        if (args[0].equalsIgnoreCase("permission") && args[1].equalsIgnoreCase("remove") && args.length == 3) {
            return List.copyOf(this.cache.allTaskPermissions().keySet());
        }
        if (args[0].equalsIgnoreCase("permission") && args[1].equalsIgnoreCase("set") && args.length == 3) {
            return List.copyOf(this.cache.allTaskPermissions().keySet());
        }
        if (args[0].equalsIgnoreCase("server_prefix") && args.length == 2) {
            return List.of("set", "show");
        }
        return List.of();
    }

    private void handlePermission(CommandSource source, String[] args) {
        if (args.length < 2) {
            usage(source);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length < 4) {
                    usage(source);
                    return;
                }
                if (!this.database.isAvailable()) {
                    this.messageService.error(source, TurnstileMsgKeys.COMMON_DB_ERROR);
                    return;
                }
                String task = args[2];
                String permission = args[3];
                this.cache.putTaskPermission(task, permission);
                this.taskPermissionRepository.upsertAsync(task, permission);
                this.messageService.success(source, TurnstileMsgKeys.PERMISSION_SET_SUCCESS,
                    MsgOpts.builder().put("task", task).put("permission", permission).build());
            }
            case "remove" -> {
                if (args.length < 3) {
                    usage(source);
                    return;
                }
                if (!this.database.isAvailable()) {
                    this.messageService.error(source, TurnstileMsgKeys.COMMON_DB_ERROR);
                    return;
                }
                String task = args[2];
                if (this.cache.removeTaskPermission(task)) {
                    this.taskPermissionRepository.deleteAsync(task);
                    this.messageService.success(source, TurnstileMsgKeys.PERMISSION_REMOVE_SUCCESS,
                        MsgOpts.with("task", task));
                } else {
                    this.messageService.error(source, TurnstileMsgKeys.PERMISSION_REMOVE_NOT_FOUND,
                        MsgOpts.with("task", task));
                }
            }
            case "list" -> sendPermissionList(source, parsePage(args, 2));
            default -> usage(source);
        }
    }

    private void sendPermissionList(CommandSource source, int page) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(this.cache.allTaskPermissions().entrySet());
        entries.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));

        this.messageService.info(source, TurnstileMsgKeys.PERMISSION_LIST_HEADER, MsgOpts.with("page", page + 1));
        int from = page * LIST_PAGE_SIZE;
        if (entries.isEmpty() || from >= entries.size()) {
            this.messageService.info(source, TurnstileMsgKeys.PERMISSION_LIST_EMPTY);
            return;
        }
        int to = Math.min(from + LIST_PAGE_SIZE, entries.size());
        for (Map.Entry<String, String> entry : entries.subList(from, to)) {
            this.messageService.info(source, TurnstileMsgKeys.PERMISSION_LIST_ROW,
                MsgOpts.builder().put("task", entry.getKey()).put("permission", entry.getValue()).noPrefix().build());
        }
        if (to < entries.size()) {
            this.messageService.info(source, TurnstileMsgKeys.PERMISSION_LIST_PAGE_FOOTER, MsgOpts.with("page", page + 2));
        }
    }

    private void handleServerPrefix(CommandSource source, String[] args) {
        if (args.length < 2) {
            usage(source);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length < 3) {
                    usage(source);
                    return;
                }
                if (!this.database.isAvailable()) {
                    this.messageService.error(source, TurnstileMsgKeys.COMMON_DB_ERROR);
                    return;
                }
                String prefix = args[2];
                this.cache.setServerPrefix(prefix);
                this.settingsRepository.saveServerPrefixAsync(prefix);
                this.messageService.success(source, TurnstileMsgKeys.SERVER_PREFIX_SET_SUCCESS,
                    MsgOpts.with("prefix_value", prefix));
            }
            case "show" -> {
                String prefix = this.cache.serverPrefix();
                if (prefix == null) {
                    this.messageService.info(source, TurnstileMsgKeys.SERVER_PREFIX_SHOW_UNSET);
                } else {
                    this.messageService.info(source, TurnstileMsgKeys.SERVER_PREFIX_SHOW,
                        MsgOpts.with("prefix_value", prefix));
                }
            }
            default -> usage(source);
        }
    }

    private static int parsePage(String[] args, int index) {
        if (args.length > index) {
            try {
                return Math.max(0, Integer.parseInt(args[index]) - 1);
            } catch (NumberFormatException ignored) {
                // fall through to first page
            }
        }
        return 0;
    }

    private void usage(CommandSource source) {
        this.messageService.info(source, TurnstileMsgKeys.COMMON_USAGE);
    }
}
