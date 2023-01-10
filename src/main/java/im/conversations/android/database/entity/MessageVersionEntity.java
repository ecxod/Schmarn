package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.database.model.Modification;
import java.time.Instant;

@Entity(
        tableName = "message_version",
        foreignKeys =
                @ForeignKey(
                        entity = MessageEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"messageId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "messageId")})
public class MessageVersionEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public long messageEntityId;
    public String messageId;
    public String stanzaId;
    public Modification modification;
    public String modifiedBy;
    public String modifiedByResource;
    public String occupantId;
    Instant receivedAt;

    // the version order is determined by the receivedAt
    // the actual display time and display order comes from the parent MessageEntity
    // the original has a receivedAt = null and stanzaId = null and inherits it's timestamp from
    // it's parent

}
