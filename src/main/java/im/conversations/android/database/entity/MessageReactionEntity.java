package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.transformer.Transformation;
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
public class MessageReactionEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long messageEntityId;

    public String stanzaId;
    public String messageId;
    public Jid reactionBy;
    public String reactionByResource;
    public String occupantId;

    public Instant receivedAt;

    public String reaction;

    public static MessageReactionEntity of(
            long messageEntityId, final String reaction, final Transformation transformation) {
        final var entity = new MessageReactionEntity();
        entity.messageEntityId = messageEntityId;
        entity.reaction = reaction;
        entity.stanzaId = transformation.stanzaId;
        entity.messageId = transformation.messageId;
        entity.reactionBy = transformation.fromBare();
        entity.reactionByResource = transformation.fromResource();
        entity.occupantId = transformation.occupantId;
        entity.receivedAt = transformation.receivedAt;
        return entity;
    }
}
