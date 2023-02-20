package im.conversations.android.database.model;

import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import org.jxmpp.jid.BareJid;

public class AccountIdentifier implements ChatFilter {

    public final long id;
    @NonNull public final BareJid address;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountIdentifier that = (AccountIdentifier) o;
        return id == that.id && Objects.equal(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, address);
    }

    public AccountIdentifier(long id, @NonNull BareJid address) {
        Preconditions.checkNotNull(address, "Account can not be instantiated without an address");
        this.id = id;
        this.address = address;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("address", address).toString();
    }
}
