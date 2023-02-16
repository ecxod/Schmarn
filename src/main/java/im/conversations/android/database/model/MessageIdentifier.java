package im.conversations.android.database.model;

import org.jxmpp.jid.BareJid;

public class MessageIdentifier {

    public final long id;
    public final String stanzaId;
    public final String messageId;
    public final BareJid fromBare;
    public final Long version;

    public MessageIdentifier(
            long id, String stanzaId, String messageId, BareJid fromBare, Long version) {
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
