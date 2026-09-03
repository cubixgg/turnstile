package dev.tobifrosch.turnstile.messaging;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Renders MiniMessage lang templates with a fixed semantic color palette and a reusable
 * {@code <prefix>} tag. Palette and prefix are hardcoded constants — there's no command-driven
 * config surface for them (unlike the DB-backed hot-reloadable palette in the original
 * {@code utils/messaging}, which depends on that project's {@code modulekit-paper} module
 * framework and doesn't apply to a standalone Velocity plugin).
 */
public final class MessageServiceImpl implements MessageService {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final String PRIMARY = "#A0C4FF";
    private static final String HL = "#FFD166";
    private static final String HL2 = "#06D6A0";
    private static final String ERROR = "#EF476F";
    private static final String WARN = "#FFB703";
    private static final String SUCCESS = "#06D6A0";
    private static final String INFO = "#8ECAE6";
    private static final String DEFAULT_PREFIX = "<dark_gray>[<primary>Turnstile</primary>]</dark_gray> ";

    private final LangLoader langLoader;
    private final TagResolver paletteResolver;
    private final Component prefixComponent;

    public MessageServiceImpl(LangLoader langLoader) {
        this.langLoader = langLoader;
        this.paletteResolver = buildPalette();
        this.prefixComponent = MINI_MESSAGE.deserialize(DEFAULT_PREFIX, this.paletteResolver);
    }

    private static TagResolver buildPalette() {
        return TagResolver.resolver(
            TagResolver.resolver("primary", Tag.styling(TextColor.fromHexString(PRIMARY))),
            TagResolver.resolver("hl", Tag.styling(TextColor.fromHexString(HL))),
            TagResolver.resolver("hl2", Tag.styling(TextColor.fromHexString(HL2))),
            TagResolver.resolver("error", Tag.styling(TextColor.fromHexString(ERROR))),
            TagResolver.resolver("warn", Tag.styling(TextColor.fromHexString(WARN))),
            TagResolver.resolver("success", Tag.styling(TextColor.fromHexString(SUCCESS))),
            TagResolver.resolver("info", Tag.styling(TextColor.fromHexString(INFO)))
        );
    }

    /**
     * Raw values are always {@link Placeholder#unparsed}, never deserialized as MiniMessage —
     * user-supplied text (a task name, a permission node) must never be able to inject tags.
     */
    private TagResolver buildResolver(MsgOpts opts) {
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(this.paletteResolver);
        builder.tag("prefix", Tag.inserting(opts.prefix() ? this.prefixComponent : Component.empty()));
        opts.placeholders().forEach((key, value) -> builder.resolver(Placeholder.unparsed(key, String.valueOf(value))));
        opts.components().forEach((key, value) -> builder.resolver(Placeholder.component(key, value)));
        return builder.build();
    }

    /**
     * Only {@code en_us} ships today, so this always resolves to it — but it reads the
     * connected player's own client locale (falling back to console/unsupported-locale
     * default) rather than a single global setting, so a future {@code lang/<locale>.yml}
     * addition lights up per-player without touching call sites.
     */
    private String resolveLocale(CommandSource to) {
        if (to instanceof Player player) {
            Locale clientLocale = player.getPlayerSettings().getLocale();
            if (clientLocale != null) {
                String candidate = clientLocale.getCountry().isEmpty()
                    ? clientLocale.getLanguage().toLowerCase(Locale.ROOT)
                    : (clientLocale.getLanguage() + "_" + clientLocale.getCountry()).toLowerCase(Locale.ROOT);
                if (this.langLoader.hasLocale(candidate)) {
                    return candidate;
                }
            }
        }
        return this.langLoader.defaultLocale();
    }

    private Component renderTemplate(CommandSource to, MsgKey key, MsgOpts opts) {
        String template = this.langLoader.getMessage(resolveLocale(to), key.path());
        return MINI_MESSAGE.deserialize(template, buildResolver(opts));
    }

    @Override
    public void info(CommandSource to, MsgKey key, MsgOpts opts) {
        to.sendMessage(renderTemplate(to, key, opts));
    }

    @Override
    public void success(CommandSource to, MsgKey key, MsgOpts opts) {
        to.sendMessage(renderTemplate(to, key, opts));
    }

    @Override
    public void warn(CommandSource to, MsgKey key, MsgOpts opts) {
        to.sendMessage(renderTemplate(to, key, opts));
    }

    @Override
    public void error(CommandSource to, MsgKey key, MsgOpts opts) {
        to.sendMessage(renderTemplate(to, key, opts));
    }

    @Override
    public Component render(CommandSource to, MsgKey key, MsgOpts opts) {
        return renderTemplate(to, key, opts);
    }

    @Override
    public Component parse(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage, this.paletteResolver);
    }
}
