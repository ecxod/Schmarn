package im.conversations.android.xmpp.manager;

import android.content.Context;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.muc.user.MultiUserChat;
import im.conversations.android.xmpp.model.stanza.Presence;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiUserChatManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiUserChatManager.class);

    public MultiUserChatManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void enter(final BareJid room) {
        final var presence = new Presence();
        presence.setTo(JidCreate.fullFrom(room, Resourcepart.fromOrThrowUnchecked("c3-test-user")));
        presence.addExtension(new MultiUserChat());
        LOGGER.info("sending {} ", presence);
        connection.sendPresencePacket(presence);
    }
}
