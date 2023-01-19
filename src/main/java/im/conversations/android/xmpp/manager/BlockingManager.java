package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.collect.Collections2;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.blocking.Block;
import im.conversations.android.xmpp.model.blocking.Blocklist;
import im.conversations.android.xmpp.model.blocking.Unblock;
import java.util.Objects;

public class BlockingManager extends AbstractManager {

    public BlockingManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handlePush(final Block block) {}

    public void handlePush(final Unblock unblock) {}

    public void fetch() {
        final IqPacket iqPacket = new IqPacket(IqPacket.TYPE.GET);
        iqPacket.addChild(new Blocklist());
        connection.sendIqPacket(iqPacket, this::handleFetchResult);
    }

    private void handleFetchResult(final IqPacket result) {
        if (result.getType() != IqPacket.TYPE.RESULT) {
            return;
        }
        final var blocklist = result.getExtension(Blocklist.class);
        if (blocklist == null) {
            return;
        }
        final var account = getAccount();
        final var items =
                blocklist.getExtensions(im.conversations.android.xmpp.model.blocking.Item.class);
        final var filteredItems = Collections2.filter(items, i -> Objects.nonNull(i.getJid()));
        getDatabase().blockingDao().setBlocklist(account, filteredItems);
    }
}