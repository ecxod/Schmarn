package im.conversations.android.xmpp.manager;

import android.content.Context;

import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.stanza.Iq;

public class JingleConnectionManager extends AbstractManager {
    public JingleConnectionManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handleJingle(Iq packet) {

    }
}
