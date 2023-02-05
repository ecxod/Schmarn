package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Account;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

@Entity(
        tableName = "axolotl_signed_pre_key",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId"},
                    unique = false)
        })
public class AxolotlSignedPreKeyEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public Integer signedPreKeyId;

    @NonNull public SignedPreKeyRecord signedPreKeyRecord;

    public boolean removed = false;

    public static AxolotlSignedPreKeyEntity of(
            Account account, int signedPreKeyId, SignedPreKeyRecord record) {
        final var entity = new AxolotlSignedPreKeyEntity();
        entity.accountId = account.id;
        entity.signedPreKeyId = signedPreKeyId;
        entity.signedPreKeyRecord = record;
        entity.removed = false;
        return entity;
    }
}
