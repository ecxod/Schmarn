package im.conversations.android.xmpp.manager;

import android.content.Context;
import im.conversations.android.xmpp.XmppConnection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AbstractManager extends XmppConnection.Delegate {

    protected static final Executor IO_EXECUTOR = Executors.newSingleThreadExecutor();

    protected AbstractManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }
}
