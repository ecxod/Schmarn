package im.conversations.android.database.model;

import androidx.room.Relation;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.MessageContentEntity;
import im.conversations.android.database.entity.MessageReactionEntity;
import java.time.Instant;
import java.util.List;

public class MessageWithContentReactions {

    public long id;

    public Instant sentAt;

    public boolean outgoing;

    public Jid toBare;
    public String toResource;
    public Jid fromBare;
    public String fromResource;

    public Modification modification;
    public long version;

    @Relation(
            entity = MessageContentEntity.class,
            parentColumn = "version",
            entityColumn = "messageVersionId")
    public List<MessageContent> contents;

    @Relation(
            entity = MessageReactionEntity.class,
            parentColumn = "id",
            entityColumn = "messageEntityId")
    public List<MessageReaction> reactions;
}
