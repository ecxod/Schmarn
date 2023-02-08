package im.conversations.android.xmpp;

import android.content.Context;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import im.conversations.android.xmpp.manager.AbstractManager;
import im.conversations.android.xmpp.manager.ArchiveManager;
import im.conversations.android.xmpp.manager.AvatarManager;
import im.conversations.android.xmpp.manager.AxolotlManager;
import im.conversations.android.xmpp.manager.BlockingManager;
import im.conversations.android.xmpp.manager.BookmarkManager;
import im.conversations.android.xmpp.manager.CarbonsManager;
import im.conversations.android.xmpp.manager.ChatStateManager;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.manager.NickManager;
import im.conversations.android.xmpp.manager.PepManager;
import im.conversations.android.xmpp.manager.PresenceManager;
import im.conversations.android.xmpp.manager.PubSubManager;
import im.conversations.android.xmpp.manager.ReceiptManager;
import im.conversations.android.xmpp.manager.RosterManager;
import im.conversations.android.xmpp.manager.StanzaIdManager;

public final class Managers {

    private Managers() {}

    public static ClassToInstanceMap<AbstractManager> initialize(
            final Context context, final XmppConnection connection) {
        return new ImmutableClassToInstanceMap.Builder<AbstractManager>()
                .put(ArchiveManager.class, new ArchiveManager(context, connection))
                .put(AvatarManager.class, new AvatarManager(context, connection))
                .put(AxolotlManager.class, new AxolotlManager(context, connection))
                .put(BlockingManager.class, new BlockingManager(context, connection))
                .put(BookmarkManager.class, new BookmarkManager(context, connection))
                .put(CarbonsManager.class, new CarbonsManager(context, connection))
                .put(ChatStateManager.class, new ChatStateManager(context, connection))
                .put(DiscoManager.class, new DiscoManager(context, connection))
                .put(NickManager.class, new NickManager(context, connection))
                .put(PepManager.class, new PepManager(context, connection))
                .put(PresenceManager.class, new PresenceManager(context, connection))
                .put(PubSubManager.class, new PubSubManager(context, connection))
                .put(ReceiptManager.class, new ReceiptManager(context, connection))
                .put(RosterManager.class, new RosterManager(context, connection))
                .put(StanzaIdManager.class, new StanzaIdManager(context, connection))
                .build();
    }
}
