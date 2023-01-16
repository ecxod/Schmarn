package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.xmpp.model.disco.info.Identity;

@Entity(
        tableName = "disco_identity",
        foreignKeys =
                @ForeignKey(
                        entity = DiscoEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"discoId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"discoId"})})
public class DiscoIdentityEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long discoId;

    public String category;
    public String type;
    public String name;

    public static DiscoIdentityEntity of(final long discoId, final Identity i) {
        final var entity = new DiscoIdentityEntity();
        entity.discoId = discoId;
        entity.category = i.getCategory();
        entity.type = i.getType();
        entity.name = i.getIdentityName();
        return entity;
    }
}
