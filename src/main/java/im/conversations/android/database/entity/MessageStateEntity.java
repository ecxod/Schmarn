package im.conversations.android.database.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.model.MessageState;
import im.conversations.android.database.model.StateType;

@Entity(
        tableName = "message_state",
        foreignKeys =
                @ForeignKey(
                        entity = MessageVersionEntity.class,
                        parentColumns = {"id"},
                        childColumns = {"messageVersionId"},
                        onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "messageVersionId")})
public class MessageStateEntity {

    @PrimaryKey(autoGenerate = true)
    public Long id;

    @NonNull public Long messageVersionId;

    @NonNull public Jid fromBare;

    @Nullable public String fromResource;

    @NonNull public StateType type;

    public String errorCondition;

    public String errorText;

    public static MessageStateEntity of(
            final long messageVersionId, final MessageState messageState) {
        final var entity = new MessageStateEntity();
        entity.messageVersionId = messageVersionId;
        entity.fromBare = messageState.fromBare;
        entity.fromResource = messageState.fromResource;
        ;
        entity.type = messageState.type;
        entity.errorCondition = messageState.errorCondition;
        entity.errorText = messageState.errorText;
        return entity;
    }
}
