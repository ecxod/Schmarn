package im.conversations.android.database.model;

import eu.siacs.conversations.xmpp.Jid;

public class MessageIdentifier {

    public final long id;
    public final String stanzaId;
    public final String messageId;
    public final Jid fromBare;
    public final Long version;

    public MessageIdentifier(
            long id, String stanzaId, String messageId, Jid fromBare, Long version) {
        this.id = id;
        this.stanzaId = stanzaId;
        this.messageId = messageId;
        this.fromBare = fromBare;
        this.version = version;
    }

    public boolean isStub() {
        return this.version == null;
    }
}
