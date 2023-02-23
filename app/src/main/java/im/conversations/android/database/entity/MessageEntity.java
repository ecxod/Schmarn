package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import im.conversations.android.transformer.MessageTransformation;
import java.time.Instant;
import java.util.Objects;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.parts.Resourcepart;

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
            @ForeignKey(
                    entity = MessageEntity.class,
                    parentColumns = {"id"},
                    childColumns = {"inReplyToMessageEntityId"},
                    onDelete = ForeignKey.SET_NULL),
        },
        indices = {
            @Index(value = "chatId"),
            @Index(value = "latestVersion"),
            @Index("inReplyToMessageEntityId")
        })
public class MessageEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long chatId;

    public Instant receivedAt;
    public Instant sentAt;

    public boolean outgoing;

    public BareJid toBare;
    public Resourcepart toResource;
    public BareJid fromBare;
    public Resourcepart fromResource;

    public String occupantId;

    public String messageId;
    public String stanzaId;
    // the stanza id might not be verified if this MessageEntity was created as a stub parent to
    // attach reactions to or new versions (created by LMC etc)
    public boolean stanzaIdVerified;

    @Nullable public Long latestVersion;

    public boolean acknowledged = false;

    public String inReplyToMessageId;
    public String inReplyToStanzaId;
    @Nullable public Long inReplyToMessageEntityId;

    public static MessageEntity of(final long chatId, final MessageTransformation transformation) {
        final var entity = new MessageEntity();
        entity.chatId = chatId;
        entity.receivedAt = transformation.receivedAt;
        entity.sentAt = transformation.sentAt();
        entity.outgoing = transformation.outgoing();
        entity.toBare = transformation.toBare();
        entity.toResource = transformation.toResource();
        entity.fromBare = transformation.fromBare();
        entity.fromResource = transformation.fromResource();
        entity.occupantId = transformation.occupantId;
        entity.messageId = transformation.messageId;
        entity.stanzaId = transformation.stanzaId;
        entity.stanzaIdVerified = Objects.nonNull(transformation.stanzaId);
        return entity;
    }

    public static MessageEntity stub(
            final long chatId, String messageId, MessageTransformation transformation) {
        final var entity = new MessageEntity();
        entity.chatId = chatId;
        entity.fromBare = transformation.fromBare();
        entity.messageId = messageId;
        entity.stanzaIdVerified = false;
        entity.occupantId = transformation.occupantId;
        return entity;
    }

    public static MessageEntity stubOfStanzaId(final long chatId, String stanzaId) {
        final var entity = new MessageEntity();
        entity.chatId = chatId;
        entity.stanzaIdVerified = false;
        entity.stanzaId = stanzaId;
        return entity;
    }

    public static MessageEntity stubOfMessageId(final long chatId, String messageId) {
        final var entity = new MessageEntity();
        entity.chatId = chatId;
        entity.stanzaIdVerified = false;
        entity.messageId = messageId;
        return entity;
    }
}
