package im.conversations.android.xmpp.manager;

import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.state.ChatStateNotification;

public class ChatStateManager extends AbstractManager{

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStateManager.class);

    public ChatStateManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handle(final Jid from, final ChatStateNotification chatState) {
        LOGGER.info("Received {} from {}", chatState, from);
    }
}
