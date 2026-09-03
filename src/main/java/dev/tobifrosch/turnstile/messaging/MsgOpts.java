package dev.tobifrosch.turnstile.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;

/**
 * Render options for a {@link MsgKey}: raw placeholder values (rendered literally, never
 * parsed as MiniMessage — see {@link MessageService}), pre-built component placeholders, and
 * whether the {@code <prefix>} tag should resolve to the plugin prefix or to nothing.
 */
public record MsgOpts(Map<String, Object> placeholders, Map<String, Component> components, boolean prefix) {

    public static MsgOpts empty() {
        return new MsgOpts(Map.of(), Map.of(), true);
    }

    public static MsgOpts noPrefix() {
        return new MsgOpts(Map.of(), Map.of(), false);
    }

    public static MsgOpts with(String key, Object value) {
        return new MsgOpts(Map.of(key, value), Map.of(), true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Object> placeholders = new LinkedHashMap<>();
        private final Map<String, Component> components = new LinkedHashMap<>();
        private boolean prefix = true;

        /** Rendered as literal text via {@code Placeholder.unparsed} — never as MiniMessage. */
        public Builder put(String key, Object value) {
            this.placeholders.put(key, value);
            return this;
        }

        public Builder put(String key, Component value) {
            this.components.put(key, value);
            return this;
        }

        public Builder noPrefix() {
            this.prefix = false;
            return this;
        }

        public MsgOpts build() {
            return new MsgOpts(Map.copyOf(this.placeholders), Map.copyOf(this.components), this.prefix);
        }
    }
}
