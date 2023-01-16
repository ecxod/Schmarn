package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "disco_ext_field",
        foreignKeys =
                @ForeignKey(
                        entity = DiscoExtensionEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"extensionId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"extensionId"})})
public class DiscoExtensionFieldEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long extensionId;

    public String field;

    public static DiscoExtensionFieldEntity of(final long extensionId, final String fieldName) {
        final var entity = new DiscoExtensionFieldEntity();
        entity.extensionId = extensionId;
        entity.field = fieldName;
        return entity;
    }
}
