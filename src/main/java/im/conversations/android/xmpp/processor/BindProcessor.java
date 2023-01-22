package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.BlockingManager;
import im.conversations.android.xmpp.manager.BookmarkManager;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.manager.RosterManager;
import java.util.function.Consumer;

public class BindProcessor extends XmppConnection.Delegate implements Consumer<Jid> {

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
            // IIRC this is used when the display name is set from a certificate or something
        }

        database.presenceDao().deletePresences(account.id);

        getManager(RosterManager.class).fetch();

        final var discoManager = getManager(DiscoManager.class);

        if (discoManager.hasFeature(account.address.getDomain(), Namespace.BLOCKING)) {
            getManager(BlockingManager.class).fetch();
        }

        if (discoManager.hasFeature(account.address.getDomain(), Namespace.COMMANDS)) {
            discoManager.items(Entity.discoItem(account.address.getDomain()), Namespace.COMMANDS);
        }

        getManager(BookmarkManager.class).fetch();

        // TODO send initial presence
    }
}
