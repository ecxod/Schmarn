package im.conversations.android.database.model;

import androidx.room.Relation;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.MessageContentEntity;
import java.time.Instant;
import java.util.List;

public class EmbeddedMessage {

    public long id;
    public Jid fromBare;
    public String fromResource;
    public Instant sentAt;

    public Long latestVersion;

    @Relation(
            entity = MessageContentEntity.class,
            parentColumn = "latestVersion",
            entityColumn = "messageVersionId")
    public List<MessageContent> contents;
}
