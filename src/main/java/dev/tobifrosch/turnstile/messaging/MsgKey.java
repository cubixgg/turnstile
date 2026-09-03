package dev.tobifrosch.turnstile.messaging;

/** A typed dot-path key into a {@code lang/<locale>.yml} file's {@code messages} tree. */
public record MsgKey(String path) {

    public static MsgKey of(String path) {
        return new MsgKey(path);
    }
}
