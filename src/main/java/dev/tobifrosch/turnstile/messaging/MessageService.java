package dev.tobifrosch.turnstile.messaging;

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;

/**
 * Renders {@link MsgKey}s from {@code lang/<locale>.yml} and sends them to a
 * {@link CommandSource}. The variant ({@code info/success/warn/error}) is expressed entirely
 * by which method is called — it is not a field on {@link MsgOpts}, so there is exactly one
 * source of truth for how a message reads (the lang template's own color tags).
 */
public interface MessageService {

    void info(CommandSource to, MsgKey key, MsgOpts opts);

    void success(CommandSource to, MsgKey key, MsgOpts opts);

    void warn(CommandSource to, MsgKey key, MsgOpts opts);

    void error(CommandSource to, MsgKey key, MsgOpts opts);

    default void info(CommandSource to, MsgKey key) {
        info(to, key, MsgOpts.empty());
    }

    default void success(CommandSource to, MsgKey key) {
        success(to, key, MsgOpts.empty());
    }

    default void warn(CommandSource to, MsgKey key) {
        warn(to, key, MsgOpts.empty());
    }

    default void error(CommandSource to, MsgKey key) {
        error(to, key, MsgOpts.empty());
    }

    Component render(CommandSource to, MsgKey key, MsgOpts opts);

    default Component render(CommandSource to, MsgKey key) {
        return render(to, key, MsgOpts.empty());
    }

    /** Parses an arbitrary MiniMessage string with the palette tags applied (no prefix, no placeholders). */
    Component parse(String miniMessage);
}
