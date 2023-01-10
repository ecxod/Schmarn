package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.time.Instant;

@Entity(
        tableName = "message_reaction",
        foreignKeys =
                @ForeignKey(
                        entity = MessageEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"messageEntityId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "messageEntityId")})
public class ReactionEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long messageEntityId;

    public String stanzaId;
    public String messageId;
    public String reactionBy;
    public String reactionByResource;
    public String occupantId;

    public Instant receivedAt;

    public String reaction;
}
