package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Account;
import org.jxmpp.jid.BareJid;
import org.whispersystems.libsignal.IdentityKey;

@Entity(
        tableName = "axolotl_identity",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId", "address", "deviceId"},
                    unique = true)
        })
public class AxolotlIdentityEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public BareJid address;

    @NonNull public Integer deviceId;

    @NonNull public IdentityKey identityKey;

    public static AxolotlIdentityEntity of(
            Account account, BareJid address, int deviceId, IdentityKey identityKey) {
        final var entity = new AxolotlIdentityEntity();
        entity.accountId = account.id;
        entity.address = address;
        entity.deviceId = deviceId;
        entity.identityKey = identityKey;
        return entity;
    }
}
