package im.conversations.android.xmpp.processor;

import android.content.Context;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.database.model.Account;
import im.conversations.android.xmpp.XmppConnection;

abstract class BaseProcessor {

    protected final Context context;
    protected final XmppConnection connection;

    BaseProcessor(final Context context, final XmppConnection connection) {
        this.context = context;
        this.connection = connection;
    }

    protected Account getAccount() {
        return connection.getAccount();
    }

    protected ConversationsDatabase getDatabase() {
        return ConversationsDatabase.getInstance(context);
    }
}
