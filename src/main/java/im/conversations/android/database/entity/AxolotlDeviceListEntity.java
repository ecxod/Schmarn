package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.time.Instant;
import org.jxmpp.jid.BareJid;

@Entity(
        tableName = "axolotl_device_list",
        foreignKeys =
                @ForeignKey(
                        entity = AccountEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"accountId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {
            @Index(
                    value = {"accountId", "address"},
                    unique = true)
        })
public class AxolotlDeviceListEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public BareJid address;

    @NonNull public Instant receivedAt;

    public String errorCondition;

    public boolean isParsingIssue;

    public static AxolotlDeviceListEntity of(long accountId, final BareJid address) {
        final var entity = new AxolotlDeviceListEntity();
        entity.accountId = accountId;
        entity.address = address;
        entity.receivedAt = Instant.now();
        entity.isParsingIssue = false;
        return entity;
    }

    public static AxolotlDeviceListEntity of(
            final long accountId, final BareJid address, final String errorCondition) {
        final var entity = new AxolotlDeviceListEntity();
        entity.accountId = accountId;
        entity.address = address;
        entity.receivedAt = Instant.now();
        entity.errorCondition = errorCondition;
        return entity;
    }

    public static AxolotlDeviceListEntity ofParsingIssue(final long account, BareJid address) {
        final var entity = new AxolotlDeviceListEntity();
        entity.accountId = account;
        entity.address = address;
        entity.receivedAt = Instant.now();
        entity.isParsingIssue = true;
        return entity;
    }
}
