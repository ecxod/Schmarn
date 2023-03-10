package im.conversations.android.database.model;

import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class AvatarWithAccount {

    public final long account;

    public final AddressWithName addressWithName;
    public final AvatarType avatarType;
    public final String hash;

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("account", account)
                .add("addressWithName", addressWithName)
                .add("avatarType", avatarType)
                .add("hash", hash)
                .toString();
    }

    public AvatarWithAccount(
            long account,
            final AddressWithName addressWithName,
            AvatarType avatarType,
            String hash) {
        this.account = account;
        this.addressWithName = addressWithName;
        this.avatarType = avatarType;
        this.hash = hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AvatarWithAccount that = (AvatarWithAccount) o;
        return account == that.account
                && Objects.equal(addressWithName, that.addressWithName)
                && avatarType == that.avatarType
                && Objects.equal(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(account, addressWithName, avatarType, hash);
    }
}
