package im.conversations.android.database.model;

import androidx.room.Relation;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import im.conversations.android.database.entity.MessageContentEntity;
import im.conversations.android.database.entity.MessageEntity;
import im.conversations.android.database.entity.MessageReactionEntity;
import im.conversations.android.database.entity.MessageStateEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jxmpp.jid.Jid;

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
    public Long inReplyToMessageEntityId;

    @Relation(
            entity = MessageEntity.class,
            parentColumn = "inReplyToMessageEntityId",
            entityColumn = "id")
    public MessageEmbedded inReplyTo;

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

    @Relation(
            entity = MessageStateEntity.class,
            parentColumn = "version",
            entityColumn = "messageVersionId")
    public List<MessageState> states;

    public Set<Map.Entry<String, Integer>> getAggregatedReactions() {
        final Map<String, Integer> aggregatedReactions =
                Maps.transformValues(
                        Multimaps.index(reactions, r -> r.reaction).asMap(), Collection::size);
        return ImmutableSortedSet.copyOf(
                (a, b) -> Integer.compare(b.getValue(), a.getValue()),
                aggregatedReactions.entrySet());
    }
}
