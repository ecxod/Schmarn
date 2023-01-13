package im.conversations.android.xmpp.processor;

import android.content.Context;
import android.util.Log;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import im.conversations.android.xmpp.XmppConnection;
import java.util.function.Consumer;

public class BindProcessor extends BaseProcessor implements Consumer<Jid> {

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
        if (Strings.isNullOrEmpty(rosterVersion)) {
            Log.d(Config.LOGTAG, account.address + ": fetching roster");
        } else {
            Log.d(Config.LOGTAG, account.address + ": fetching roster version " + rosterVersion);
        }
        iqPacket.query(Namespace.ROSTER).setAttribute("ver", rosterVersion);
        connection.sendIqPacket(iqPacket, result -> {});
    }
}
