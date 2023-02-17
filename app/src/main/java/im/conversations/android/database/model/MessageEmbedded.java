package im.conversations.android.database.model;

import androidx.room.Relation;
import im.conversations.android.database.entity.MessageContentEntity;
import java.time.Instant;
import java.util.List;
import org.jxmpp.jid.Jid;

public class MessageEmbedded {

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
