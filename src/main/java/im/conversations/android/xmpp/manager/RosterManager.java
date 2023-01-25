package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.roster.Item;
import im.conversations.android.xmpp.model.roster.Query;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RosterManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RosterManager.class);

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
        final Iq iqPacket = new Iq(Iq.Type.GET);
        final Query rosterQuery = new Query();
        iqPacket.addChild(rosterQuery);
        if (Strings.isNullOrEmpty(rosterVersion)) {
            LOGGER.info("{}: fetching roster", account.address);
        } else {
            LOGGER.info("{}: fetching roster version {}", account.address, rosterVersion);
            rosterQuery.setVersion(rosterVersion);
        }
        connection.sendIqPacket(iqPacket, this::handleFetchResult);
    }

    private void handleFetchResult(final Iq result) {
        if (result.getType() != Iq.Type.RESULT) {
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
        // In a roster result (Section 2.1.4), the client MUST ignore values of the 'subscription'
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
