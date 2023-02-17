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

public class Account {

    public final long id;
    @NonNull public final BareJid address;
    @NonNull public final byte[] randomSeed;

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", address=" + address +
                ", randomSeed=" + Arrays.toString(randomSeed) +
                '}';
    }

    public Account(final long id, @NonNull final BareJid address, @NonNull byte[] randomSeed) {
        Preconditions.checkNotNull(address, "Account can not be instantiated without an address");
        Preconditions.checkArgument(
                randomSeed.length == 32, "RandomSeed must have exactly 32 bytes");
        this.id = id;
        this.address = address;
        this.randomSeed = randomSeed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return id == account.id
                && Objects.equal(address, account.address)
                && Arrays.equals(randomSeed, account.randomSeed);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, address, randomSeed);
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
