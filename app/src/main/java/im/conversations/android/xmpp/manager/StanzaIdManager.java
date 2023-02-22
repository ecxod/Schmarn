package im.conversations.android.xmpp.manager;

import android.content.Context;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.stanza.Message;
import im.conversations.android.xmpp.model.unique.StanzaId;
import org.jxmpp.jid.Jid;

public class StanzaIdManager extends AbstractManager {

    public StanzaIdManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public String getStanzaId(final Message message) {
        final Jid by;
        if (message.getType() == Message.Type.GROUPCHAT) {
            final var from = message.getFrom();
            if (from == null) {
                return null;
            }
            by = from.asBareJid();
        } else {
            by = connection.getBoundAddress().asBareJid();
        }
        if (message.hasExtension(StanzaId.class)
                && getManager(DiscoManager.class)
                        .hasFeature(Entity.discoItem(by), Namespace.STANZA_IDS)) {
            return getStanzaIdBy(message, by);
        } else {
            return null;
        }
    }

    private static String getStanzaIdBy(final Message message, final Jid by) {
        for (final StanzaId stanzaId : message.getExtensions(StanzaId.class)) {
            final var id = stanzaId.getId();
            if (by.equals(stanzaId.getBy()) && id != null) {
                return id;
            }
        }
        return null;
    }
}
