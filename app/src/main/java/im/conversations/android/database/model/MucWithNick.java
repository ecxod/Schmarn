package im.conversations.android.database.model;

import com.google.common.base.MoreObjects;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.parts.Resourcepart;

public class MucWithNick {
    public final long chatId;

    public final BareJid address;
    private final String nickBookmark;
    private final String nickAccount;

    public MucWithNick(
            final long chatId,
            final BareJid address,
            final String nickBookmark,
            final String nickAccount) {
        this.chatId = chatId;
        this.address = address;
        this.nickBookmark = nickBookmark;
        this.nickAccount = nickAccount;
    }

    public Resourcepart nick() {
        final var bookmark = nickBookmark == null ? null : Resourcepart.fromOrNull(nickBookmark);
        final var account = nickAccount == null ? null : Resourcepart.fromOrNull(nickAccount);
        return bookmark != null ? bookmark : account;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("address", address)
                .add("nickBookmark", nickBookmark)
                .add("nickAccount", nickAccount)
                .toString();
    }
}
