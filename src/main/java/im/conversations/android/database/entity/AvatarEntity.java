package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.model.Account;
import im.conversations.android.database.model.AvatarThumbnail;
import im.conversations.android.xmpp.model.avatar.Info;

@Entity(
        tableName = "avatar",
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
public class AvatarEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long accountId;

    @NonNull public Jid address;

    @Embedded(prefix = "thumb_")
    @NonNull
    public AvatarThumbnail thumbnail;

    public static AvatarEntity of(final Account account, final Jid address, final Info info) {
        final var entity = new AvatarEntity();
        entity.accountId = account.id;
        entity.address = address;
        entity.thumbnail = AvatarThumbnail.of(info);
        return entity;
    }
}
