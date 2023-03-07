package im.conversations.android.database.model;

import com.google.common.base.Objects;
import org.jxmpp.jid.Jid;

public class AddressWithName {

    public final Jid address;
    public final String name;

    public AddressWithName(Jid address, String name) {
        this.address = address;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddressWithName that = (AddressWithName) o;
        return Objects.equal(address, that.address) && Objects.equal(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(address, name);
    }
}
