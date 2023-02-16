package im.conversations.android.database;

import androidx.room.TypeConverter;
import com.google.common.base.Strings;
import java.io.IOException;
import java.time.Instant;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

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
    public static BareJid toBareJid(final String input) {
        return input == null ? null : JidCreate.bareFromOrThrowUnchecked(input);
    }

    @TypeConverter
    public static String fromBareJid(final BareJid jid) {
        return jid == null ? null : jid.toString();
    }

    @TypeConverter
    public static String fromJid(final Jid jid) {
        return jid == null ? null : jid.toString();
    }

    @TypeConverter
    public static Jid toJid(final String input) {
        return input == null ? null : JidCreate.fromOrThrowUnchecked(input);
    }

    @TypeConverter
    public static Resourcepart toResourcePart(final String input) {
        return Strings.isNullOrEmpty(input)
                ? Resourcepart.EMPTY
                : Resourcepart.fromOrThrowUnchecked(input);
    }

    @TypeConverter
    public static String fromResourcePart(final Resourcepart resourcepart) {
        return resourcepart == null ? null : resourcepart.toString();
    }

    @TypeConverter
    public static byte[] fromIdentityKey(final IdentityKey identityKey) {
        return identityKey == null ? null : identityKey.serialize();
    }

    @TypeConverter
    public static IdentityKey toIdentityKey(final byte[] serialized) {
        if (serialized == null || serialized.length == 0) {
            return null;
        }
        try {
            return new IdentityKey(serialized, 0);
        } catch (final InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @TypeConverter
    public static byte[] fromSessionRecord(final SessionRecord sessionRecord) {
        return sessionRecord == null ? null : sessionRecord.serialize();
    }

    @TypeConverter
    public static SessionRecord toSessionRecord(final byte[] serialized) {
        if (serialized == null || serialized.length == 0) {
            return null;
        }
        try {
            return new SessionRecord(serialized);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @TypeConverter
    public static byte[] fromSignedPreKey(final SignedPreKeyRecord signedPreKeyRecord) {
        return signedPreKeyRecord == null ? null : signedPreKeyRecord.serialize();
    }

    @TypeConverter
    public static SignedPreKeyRecord toSignedPreKey(final byte[] serialized) {
        if (serialized == null || serialized.length == 0) {
            return null;
        }
        try {
            return new SignedPreKeyRecord(serialized);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @TypeConverter
    public static byte[] fromPreKey(final PreKeyRecord preKeyRecord) {
        return preKeyRecord == null ? null : preKeyRecord.serialize();
    }

    @TypeConverter
    public static PreKeyRecord toPreKey(final byte[] serialized) {
        if (serialized == null || serialized.length == 0) {
            return null;
        }
        try {
            return new PreKeyRecord(serialized);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @TypeConverter
    public static byte[] fromIdentityKeyPair(final IdentityKeyPair identityKeyPair) {
        return identityKeyPair == null ? null : identityKeyPair.serialize();
    }

    @TypeConverter
    public static IdentityKeyPair toIdentityKeyPair(final byte[] serialized) {
        if (serialized == null || serialized.length == 0) {
            return null;
        }
        try {
            return new IdentityKeyPair(serialized);
        } catch (final InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
