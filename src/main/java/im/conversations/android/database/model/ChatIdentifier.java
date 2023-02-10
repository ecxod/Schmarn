package im.conversations.android.database.model;

import eu.siacs.conversations.xmpp.Jid;

public class ChatIdentifier {

    public final long id;
    public final Jid address;
    public final ChatType type;
    public final boolean archived;

    public ChatIdentifier(long id, Jid address, ChatType type, final boolean archived) {
        this.id = id;
        this.address = address;
        this.type = type;
        this.archived = archived;
    }
}
