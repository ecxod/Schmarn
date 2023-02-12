package im.conversations.android.database.model;

import eu.siacs.conversations.xmpp.Jid;

public class MessageReaction {

    public final Jid reactionBy;
    public final String reactionByResource;
    public final String occupantId;

    public final String reaction;

    public MessageReaction(
            Jid reactionBy, String reactionByResource, String occupantId, String reaction) {
        this.reactionBy = reactionBy;
        this.reactionByResource = reactionByResource;
        this.occupantId = occupantId;
        this.reaction = reaction;
    }
}
