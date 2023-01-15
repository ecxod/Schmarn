package im.conversations.android.xmpp.manager;

import android.content.Context;
import android.util.Log;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.roster.Item;
import im.conversations.android.xmpp.model.roster.Query;
import java.util.Objects;

public class RosterManager extends AbstractManager {

    public RosterManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public void handlePush(final Query query) {
        final var version = query.getVersion();
        final var items = query.getExtensions(Item.class);
        getDatabase().rosterDao().update(getAccount(), version, items);
    }

    public void fetch() {
        final var account = getAccount();
        final var database = getDatabase();
        final String rosterVersion = database.accountDao().getRosterVersion(account.id);
        final IqPacket iqPacket = new IqPacket(IqPacket.TYPE.GET);
        final Query rosterQuery = new Query();
        iqPacket.addChild(rosterQuery);
        if (Strings.isNullOrEmpty(rosterVersion)) {
            Log.d(Config.LOGTAG, account.address + ": fetching roster");
        } else {
            Log.d(Config.LOGTAG, account.address + ": fetching roster version " + rosterVersion);
            rosterQuery.setVersion(rosterVersion);
        }
        connection.sendIqPacket(iqPacket, this::handleFetchResult);
    }

    private void handleFetchResult(final IqPacket result) {
        if (result.getType() != IqPacket.TYPE.RESULT) {
            return;
        }
        final var query = result.getExtension(Query.class);
        if (query == null) {
            // No query in result means further modifications are sent via pushes
            return;
        }
        final var account = getAccount();
        final var database = getDatabase();
        final var version = query.getVersion();
        final var items = query.getExtensions(Item.class);
        // In a roster result (Section 2.1.4), the client MUST ignore values of the c'subscription'
        // attribute other than "none", "to", "from", or "both".
        final var validItems =
                Collections2.filter(
                        items,
                        i ->
                                Item.RESULT_SUBSCRIPTIONS.contains(i.getSubscription())
                                        && Objects.nonNull(i.getJid()));
        database.rosterDao().set(account, version, validItems);
    }
}
