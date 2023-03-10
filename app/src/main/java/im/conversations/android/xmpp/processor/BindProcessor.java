package im.conversations.android.xmpp.processor;

import android.content.Context;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.Range;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.ArchiveManager;
import im.conversations.android.xmpp.manager.AxolotlManager;
import im.conversations.android.xmpp.manager.BlockingManager;
import im.conversations.android.xmpp.manager.BookmarkManager;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.manager.PresenceManager;
import im.conversations.android.xmpp.manager.RosterManager;
import java.util.List;
import java.util.function.Consumer;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindProcessor extends XmppConnection.Delegate implements Consumer<Jid> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BindProcessor.class);

    public BindProcessor(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    @Override
    public void accept(final Jid jid) {
        final var account = getAccount();
        final var database = getDatabase();
        final var archive = jid.asBareJid();
        final List<Range> catchUpQueryRanges =
                database.runInTransaction(
                        () -> {
                            database.chatDao().resetMucStates();
                            database.presenceDao().deletePresences(account.id);
                            database.discoDao().deleteUnused(account.id);
                            return database.archiveDao().resetLivePage(account, archive);
                        });

        getManager(RosterManager.class).fetch();

        final var discoManager = getManager(DiscoManager.class);

        if (discoManager.hasServerFeature(Namespace.BLOCKING)) {
            getManager(BlockingManager.class).fetch();
        }

        if (discoManager.hasServerFeature(Namespace.COMMANDS)) {
            discoManager.items(
                    Entity.discoItem(account.address.asDomainBareJid()), Namespace.COMMANDS);
        }

        getManager(BookmarkManager.class).fetch();

        getManager(AxolotlManager.class).publishIfNecessary();

        getManager(ArchiveManager.class).query(archive, catchUpQueryRanges);

        getManager(PresenceManager.class).sendPresence();
    }
}
