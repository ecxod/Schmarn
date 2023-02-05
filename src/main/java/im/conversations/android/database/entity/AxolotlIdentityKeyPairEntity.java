package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Account;
import org.whispersystems.libsignal.IdentityKeyPair;

@Entity(
        tableName = "axolotl_identity_key_pair",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId"},
                    unique = true)
        })
public class AxolotlIdentityKeyPairEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public IdentityKeyPair identityKeyPair;

    public static AxolotlIdentityKeyPairEntity of(
            final Account account, final IdentityKeyPair identityKeyPair) {
        final var entity = new AxolotlIdentityKeyPairEntity();
        entity.accountId = account.id;
        entity.identityKeyPair = identityKeyPair;
        return entity;
    }
}
