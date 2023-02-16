package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Account;
import org.jxmpp.jid.BareJid;
import org.whispersystems.libsignal.state.SessionRecord;

@Entity(
        tableName = "axolotl_session",
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
public class AxolotlSessionEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public BareJid address;

    @NonNull public Integer deviceId;

    @NonNull public SessionRecord sessionRecord;

    public static AxolotlSessionEntity of(
            Account account, BareJid address, int deviceId, SessionRecord record) {
        final var entity = new AxolotlSessionEntity();
        entity.accountId = account.id;
        entity.address = address;
        entity.deviceId = deviceId;
        entity.sessionRecord = record;
        return entity;
    }
}
