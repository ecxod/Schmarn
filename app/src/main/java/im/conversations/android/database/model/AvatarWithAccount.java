package im.conversations.android.database.model;

import com.google.common.base.Objects;

public class AvatarWithAccount {

    public final long account;

    public final AddressWithName addressWithName;
    public final AvatarType avatarType;
    public final String hash;

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
