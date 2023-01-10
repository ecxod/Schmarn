package im.conversations.android.database;

import androidx.room.TypeConverter;
import eu.siacs.conversations.xmpp.Jid;
import java.time.Instant;

public final class Converters {

    private Converters() {}

    @TypeConverter
    public static Instant toInstant(final Long timestamp) {
        return timestamp == null ? null : Instant.ofEpochMilli(timestamp);
    }

    @TypeConverter
    public static Long fromInstant(final Instant instant) {
        return instant == null ? null : instant.getEpochSecond() * 1000;
    }

    @TypeConverter
    public static Jid toJid(final String input) {
        return input == null ? null : Jid.ofEscaped(input);
    }

    @TypeConverter
    public static String fromJid(final Jid jid) {
        return jid == null ? null : jid.toEscapedString();
    }
}
