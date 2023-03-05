package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.nick.Nick;
import im.conversations.android.xmpp.model.pubsub.Items;
import org.jxmpp.jid.BareJid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NickManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NickManager.class);

    public NickManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handleItems(final BareJid from, Items items) {
        final var item = items.getFirstItem(Nick.class);
        final var nick = item == null ? null : item.getContent();
        if (from == null || Strings.isNullOrEmpty(nick)) {
            return;
        }
        getDatabase().nickDao().set(getAccount(), from.asBareJid(), nick);
    }

    public ListenableFuture<Void> publishNick(final String name) {
        final Nick nick = new Nick();
        nick.setContent(name);
        return getManager(PepManager.class).publishSingleton(nick, NodeConfiguration.PRESENCE);
    }
}
