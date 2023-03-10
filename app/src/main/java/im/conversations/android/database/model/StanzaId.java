package im.conversations.android.database.model;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import org.jxmpp.jid.Jid;

public class StanzaId {

    public final String id;
    public final Jid by;

    public StanzaId(String id, Jid by) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(by);
        this.id = id;
        this.by = by;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("by", by).toString();
    }
}
