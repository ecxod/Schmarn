package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.PartType;

@Entity(
        tableName = "message_part",
        foreignKeys =
                @ForeignKey(
                        entity = MessageVersionEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"messageVersionId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "messageVersionId")})
public class MessagePartEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long messageVersionId;

    public String language;

    public PartType type;

    public String body;

    public String url;
}
