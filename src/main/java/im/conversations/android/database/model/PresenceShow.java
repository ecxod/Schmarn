package im.conversations.android.database.model;

import java.util.Locale;

public enum PresenceShow {
    CHAT,
    AWAY,
    XA,
    DND;

    public static PresenceShow of(final String value) {
        try {
            return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
