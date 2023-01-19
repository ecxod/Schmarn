package im.conversations.android.database.model;

import androidx.annotation.Nullable;
import java.util.Locale;

public enum PresenceType {
    UNAVAILABLE,
    ERROR,
    SUBSCRIBE;

    public static PresenceType of(@Nullable String typeAttribute) {
        if (typeAttribute == null) {
            return null;
        }
        return of(typeAttribute.toUpperCase(Locale.ROOT));
    }
}
