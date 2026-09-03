package dev.tobifrosch.turnstile.messaging;

/** Every {@link MsgKey} used by Turnstile, matching {@code lang/en_us.yml}'s key layout. */
public final class TurnstileMsgKeys {

    public static final MsgKey GATE_DENIED_PREFIX = MsgKey.of("gate.denied.prefix");
    public static final MsgKey GATE_DENIED_PERMISSION = MsgKey.of("gate.denied.permission");

    public static final MsgKey SERVER_UNKNOWN = MsgKey.of("server.unknown");
    public static final MsgKey SERVER_LIST = MsgKey.of("server.list");
    public static final MsgKey SERVER_LIST_EMPTY = MsgKey.of("server.list_empty");

    public static final MsgKey PERMISSION_SET_SUCCESS = MsgKey.of("permission.set.success");
    public static final MsgKey PERMISSION_REMOVE_SUCCESS = MsgKey.of("permission.remove.success");
    public static final MsgKey PERMISSION_REMOVE_NOT_FOUND = MsgKey.of("permission.remove.not_found");
    public static final MsgKey PERMISSION_LIST_HEADER = MsgKey.of("permission.list.header");
    public static final MsgKey PERMISSION_LIST_ROW = MsgKey.of("permission.list.row");
    public static final MsgKey PERMISSION_LIST_EMPTY = MsgKey.of("permission.list.empty");
    public static final MsgKey PERMISSION_LIST_PAGE_FOOTER = MsgKey.of("permission.list.page_footer");

    public static final MsgKey SERVER_PREFIX_SET_SUCCESS = MsgKey.of("server_prefix.set.success");
    public static final MsgKey SERVER_PREFIX_SHOW = MsgKey.of("server_prefix.show");
    public static final MsgKey SERVER_PREFIX_SHOW_UNSET = MsgKey.of("server_prefix.show_unset");

    public static final MsgKey COMMON_NO_PERMISSION = MsgKey.of("common.no_permission");
    public static final MsgKey COMMON_DB_ERROR = MsgKey.of("common.db_error");
    public static final MsgKey COMMON_USAGE = MsgKey.of("common.usage");

    private TurnstileMsgKeys() {
    }
}
