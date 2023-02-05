package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Account;
import org.whispersystems.libsignal.state.PreKeyRecord;

@Entity(
        tableName = "axolotl_pre_key",
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
public class AxolotlPreKeyEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public Integer preKeyId;

    @NonNull public PreKeyRecord preKeyRecord;

    public boolean removed = false;

    public static AxolotlPreKeyEntity of(Account account, int preKeyId, PreKeyRecord preKeyRecord) {
        final var entity = new AxolotlPreKeyEntity();
        entity.accountId = account.id;
        entity.preKeyId = preKeyId;
        entity.preKeyRecord = preKeyRecord;
        entity.removed = false;
        return entity;
    }
}
