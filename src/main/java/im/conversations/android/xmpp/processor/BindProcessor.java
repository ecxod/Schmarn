package im.conversations.android.xmpp.processor;

import android.content.Context;
import android.util.Log;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.blocking.Blocklist;
import im.conversations.android.xmpp.model.roster.Item;
import im.conversations.android.xmpp.model.roster.Query;
import java.util.Objects;
import java.util.function.Consumer;

public class BindProcessor extends AbstractBaseProcessor implements Consumer<Jid> {

    public BindProcessor(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    @Override
    public void accept(final Jid jid) {
        final var account = getAccount();
        final var database = getDatabase();

        final boolean firstLogin =
                database.accountDao().setLoggedInSuccessfully(account.id, true) > 0;

        if (firstLogin) {
            // TODO publish display name if this is the first attempt
            // iirc this is used when the display name is set from a certificate or something
        }

        database.presenceDao().deletePresences(account.id);

        fetchRoster();

        // TODO check feature
        fetchBlocklist();

        // TODO fetch bookmarks

        // TODO send initial presence
    }

    private void fetchRoster() {
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
        connection.sendIqPacket(iqPacket, this::handleFetchRosterResult);
    }

    private void handleFetchRosterResult(final IqPacket result) {
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

    private void fetchBlocklist() {
        final IqPacket iqPacket = new IqPacket(IqPacket.TYPE.GET);
        iqPacket.addChild(new Blocklist());
        connection.sendIqPacket(iqPacket, this::handleFetchBlocklistResult);
    }

    private void handleFetchBlocklistResult(final IqPacket result) {
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
