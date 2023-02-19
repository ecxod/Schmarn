package im.conversations.android.database.model;

import androidx.annotation.NonNull;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Ints;
import im.conversations.android.IDs;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.jxmpp.jid.BareJid;

public class Account extends AccountIdentifier {

    @NonNull public final byte[] randomSeed;

    public Account(final long id, @NonNull final BareJid address, @NonNull byte[] randomSeed) {
        super(id, address);
        Preconditions.checkArgument(
                randomSeed.length == 32, "RandomSeed must have exactly 32 bytes");
        this.randomSeed = randomSeed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Account account = (Account) o;
        return Arrays.equals(randomSeed, account.randomSeed);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), randomSeed);
    }

    public boolean isOnion() {
        final String domain = address.getDomain().toString();
        return domain.endsWith(".onion");
    }

    public UUID getPublicDeviceId() {
        try {
            return IDs.uuid(
                    ByteSource.wrap(randomSeed).slice(0, 16).hash(Hashing.sha256()).asBytes());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getPublicDeviceIdInt() {
        try {
            return Math.abs(Ints.fromByteArray(ByteSource.wrap(randomSeed).slice(0, 4).read()));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
