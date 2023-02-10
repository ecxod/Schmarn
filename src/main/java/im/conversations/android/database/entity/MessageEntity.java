package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.transformer.Transformation;
import java.time.Instant;
import java.util.Objects;

@Entity(
        tableName = "message",
        foreignKeys = {
            @ForeignKey(
                    entity = ChatEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"chatId"},
                    onDelete = ForeignKey.CASCADE),
            @ForeignKey(
                    entity = MessageVersionEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"latestVersion"},
                    onDelete = ForeignKey.CASCADE),
        },
        indices = {@Index(value = "chatId"), @Index(value = "latestVersion")})
public class MessageEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long chatId;

    public Instant receivedAt;
    public Instant sentAt;

    public boolean outgoing;

    public Jid toBare;
    public String toResource;
    public Jid fromBare;
    public String fromResource;

    public String occupantId;

    public String messageId;
    public String stanzaId;
    // the stanza id might not be verified if this MessageEntity was created as a stub parent to
    // attach reactions to or new versions (created by LMC etc)
    public boolean stanzaIdVerified;

    @Nullable public Long latestVersion;

    public boolean acknowledged = false;

    public static MessageEntity of(final long chatId, final Transformation transformation) {
        final var entity = new MessageEntity();
        entity.chatId = chatId;
        entity.receivedAt = transformation.receivedAt;
        entity.sentAt = transformation.sentAt();
        entity.outgoing = transformation.outgoing();
        entity.toBare = transformation.toBare();
        entity.toResource = transformation.toResource();
        entity.fromBare = transformation.fromBare();
        entity.fromResource = transformation.fromResource();
        entity.messageId = transformation.messageId;
        entity.stanzaId = transformation.stanzaId;
        entity.stanzaIdVerified = Objects.nonNull(transformation.stanzaId);
        return entity;
    }
}
