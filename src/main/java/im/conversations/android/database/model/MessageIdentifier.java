package im.conversations.android.database.model;

import com.google.common.base.MoreObjects;
import eu.siacs.conversations.xmpp.Jid;

public class MessageIdentifier {

    public final long id;
    public final String stanzaId;
    public final String messageId;
    public final Jid fromBare;
    public final Long latestVersion;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("stanzaId", stanzaId)
                .add("messageId", messageId)
                .add("fromBare", fromBare)
                .add("latestVersion", latestVersion)
                .toString();
    }

    public MessageIdentifier(
            long id, String stanzaId, String messageId, Jid fromBare, Long latestVersion) {
        this.id = id;
        this.stanzaId = stanzaId;
        this.messageId = messageId;
        this.fromBare = fromBare;
        this.latestVersion = latestVersion;
    }

    public boolean isStub() {
        return this.latestVersion == null;
    }
}
