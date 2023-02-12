package im.conversations.android.database.model;

import androidx.room.Relation;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.MessageContentEntity;
import im.conversations.android.database.entity.MessageReactionEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public Set<Map.Entry<String, Integer>> getAggregatedReactions() {
        final Map<String, Integer> aggregatedReactions =
                Maps.transformValues(
                        Multimaps.index(reactions, r -> r.reaction).asMap(), Collection::size);
        return ImmutableSortedSet.copyOf(
                (a, b) -> Integer.compare(b.getValue(), a.getValue()),
                aggregatedReactions.entrySet());
    }
}
