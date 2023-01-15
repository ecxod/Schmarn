package im.conversations.android.xmpp.manager;

import android.content.Context;
import im.conversations.android.xmpp.XmppConnection;

public class AbstractManager extends XmppConnection.Delegate {

    protected AbstractManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }
}
