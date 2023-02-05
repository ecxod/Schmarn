package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.base.Strings;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.nick.Nick;
import im.conversations.android.xmpp.model.pubsub.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NickManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NickManager.class);

    public NickManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handleItems(final Jid from, Items items) {
        final var item = items.getFirstItem(Nick.class);
        final var nick = item == null ? null : item.getContent();
        if (from == null || Strings.isNullOrEmpty(nick)) {
            return;
        }
        getDatabase().nickDao().set(getAccount(), from.asBareJid(), nick);
    }
}
