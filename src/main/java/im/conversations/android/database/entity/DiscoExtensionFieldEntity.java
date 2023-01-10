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
}
