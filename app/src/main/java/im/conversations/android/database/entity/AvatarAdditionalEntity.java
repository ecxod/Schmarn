package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.AvatarExternal;
import im.conversations.android.xmpp.model.avatar.Info;

@Entity(
        tableName = "avatar_additional",
        foreignKeys =
                @ForeignKey(
                        entity = AvatarEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"avatarId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"avatarId"})})
public class AvatarAdditionalEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long avatarId;

    @NonNull
    @Embedded(prefix = "avatar_external_")
    public AvatarExternal external;

    public static AvatarAdditionalEntity of(final long avatarId, Info info) {
        final var entity = new AvatarAdditionalEntity();
        entity.avatarId = avatarId;
        entity.external = AvatarExternal.of(info);
        return entity;
    }
}
