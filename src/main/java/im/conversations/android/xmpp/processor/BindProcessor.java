package im.conversations.android.xmpp.processor;

import android.content.Context;
import android.util.Log;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.roster.Item;
import im.conversations.android.xmpp.model.roster.Query;
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
        connection.sendIqPacket(
                iqPacket,
                result -> {
                    if (result.getType() != IqPacket.TYPE.RESULT) {
                        return;
                    }
                    final Query query = result.getExtension(Query.class);
                    if (query == null) {
                        // No query in result means further modifications are sent via pushes
                        return;
                    }
                    // TODO delete entire roster
                    for (final Item item : query.getExtensions(Item.class)) {}
                });
    }
}
