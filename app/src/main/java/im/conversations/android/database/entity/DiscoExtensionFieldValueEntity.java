package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "disco_ext_field_value",
        foreignKeys =
                @ForeignKey(
                        entity = DiscoExtensionFieldEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"fieldId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"fieldId"})})
public class DiscoExtensionFieldValueEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long fieldId;

    public String value;

    public static DiscoExtensionFieldValueEntity of(long fieldId, final String value) {
        final var entity = new DiscoExtensionFieldValueEntity();
        entity.fieldId = fieldId;
        entity.value = value;
        return entity;
    }
}
