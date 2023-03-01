package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.Trust;
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
                    value = {"accountId", "address", "identityKey"},
                    unique = true)
        })
public class AxolotlIdentityEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public BareJid address;

    @NonNull public IdentityKey identityKey;

    @NonNull public Trust trust;

    public static AxolotlIdentityEntity of(
            final Account account,
            final BareJid address,
            final IdentityKey identityKey,
            final Trust trust) {
        final var entity = new AxolotlIdentityEntity();
        entity.accountId = account.id;
        entity.address = address;
        entity.identityKey = identityKey;
        entity.trust = trust;
        return entity;
    }
}
