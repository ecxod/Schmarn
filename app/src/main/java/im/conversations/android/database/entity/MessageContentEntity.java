package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.MessageContent;
import im.conversations.android.database.model.PartType;

@Entity(
        tableName = "message_content",
        foreignKeys =
                @ForeignKey(
                        entity = MessageVersionEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"messageVersionId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "messageVersionId")})
public class MessageContentEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long messageVersionId;

    public String language;

    public PartType type;

    public String body;

    public String url;

    public static MessageContentEntity of(
            final long messageVersionId, final MessageContent content) {
        final var entity = new MessageContentEntity();
        entity.messageVersionId = messageVersionId;
        entity.language = content.language;
        entity.type = content.type;
        entity.body = content.body;
        entity.url = content.url;
        return entity;
    }
}
