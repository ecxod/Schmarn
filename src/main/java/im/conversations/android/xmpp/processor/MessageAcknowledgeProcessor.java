package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.XmppConnection;
import java.util.function.BiFunction;

public class MessageAcknowledgeProcessor extends AbstractBaseProcessor
        implements BiFunction<Jid, String, Boolean> {

    public MessageAcknowledgeProcessor(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    @Override
    public Boolean apply(final Jid to, final String id) {
        final var account = getAccount();
        return getDatabase().messageDao().acknowledge(account, id, to);
    }
}
