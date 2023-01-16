package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "disco_ext",
        foreignKeys =
                @ForeignKey(
                        entity = DiscoEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"discoId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"discoId"})})
public class DiscoExtensionEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long discoId;

    public static DiscoExtensionEntity of(long discoId) {
        final var entity = new DiscoExtensionEntity();
        entity.discoId = discoId;
        return entity;
    }
}
