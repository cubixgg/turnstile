package dev.tobifrosch.turnstile.messaging;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads {@code lang/<locale>.yml} files from the plugin jar and resolves dot-path keys under
 * each one's {@code messages} tree. Only {@code en_us} ships today, but the loader stays
 * multi-locale-shaped (a locale -> messages-tree map with a default fallback) so a translator
 * can drop in another {@code lang/<locale>.yml} later without touching {@link MessageServiceImpl}.
 */
public final class LangLoader {

    public static final String DEFAULT_LOCALE = "en_us";

    /** Every locale file bundled in {@code src/main/resources/lang}. Extend when adding one. */
    private static final List<String> BUNDLED_LOCALES = List.of("en_us");

    private final Map<String, Map<String, Object>> messagesByLocale;
    private final String defaultLocale;

    private LangLoader(Map<String, Map<String, Object>> messagesByLocale, String defaultLocale) {
        this.messagesByLocale = messagesByLocale;
        this.defaultLocale = defaultLocale;
    }

    public static LangLoader load(Logger logger) {
        Map<String, Map<String, Object>> messagesByLocale = new HashMap<>();
        for (String locale : BUNDLED_LOCALES) {
            messagesByLocale.put(locale, loadOne(logger, locale));
        }
        return new LangLoader(messagesByLocale, DEFAULT_LOCALE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadOne(Logger logger, String locale) {
        String resourcePath = "lang/" + locale + ".yml";
        try (InputStream in = LangLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                logger.error("{} is missing from the plugin jar — every message will show as [Missing: ...]",
                    resourcePath);
                return Map.of();
            }
            Map<String, Object> root = new Yaml().load(in);
            Object messages = root != null ? root.get("messages") : null;
            return messages instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (Exception e) {
            logger.error("Failed to load {} — every message will show as [Missing: ...]", resourcePath, e);
            return Map.of();
        }
    }

    public String defaultLocale() {
        return this.defaultLocale;
    }

    public boolean hasLocale(String locale) {
        return this.messagesByLocale.containsKey(locale);
    }

    public String getMessage(String locale, String keyPath) {
        Object value = lookup(locale, keyPath);
        if (!(value instanceof String) && !locale.equals(this.defaultLocale)) {
            value = lookup(this.defaultLocale, keyPath);
        }
        return value instanceof String s ? s : "<prefix><warn>[Missing: " + keyPath + "]</warn>";
    }

    private Object lookup(String locale, String keyPath) {
        Object value = this.messagesByLocale.get(locale);
        for (String part : keyPath.split("\\.")) {
            if (!(value instanceof Map<?, ?> map)) {
                return null;
            }
            value = map.get(part);
        }
        return value;
    }
}
