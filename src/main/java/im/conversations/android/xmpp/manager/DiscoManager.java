package im.conversations.android.xmpp.manager;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.disco.items.Item;
import im.conversations.android.xmpp.model.disco.items.ItemsQuery;
import java.util.Collection;

public class DiscoManager extends AbstractManager {

    public DiscoManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<InfoQuery> info(final Jid entity) {
        final var iqPacket = new IqPacket(IqPacket.TYPE.GET);
        iqPacket.setTo(entity);
        iqPacket.addChild(new InfoQuery());
        final var future = connection.sendIqPacket(iqPacket);
        return Futures.transform(
                future,
                iqResult -> {
                    final var infoQuery = iqResult.getExtension(InfoQuery.class);
                    if (infoQuery == null) {
                        throw new IllegalStateException();
                    }
                    // TODO store query
                    return infoQuery;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Collection<Item>> items(final Jid entity) {
        final var iqPacket = new IqPacket(IqPacket.TYPE.GET);
        iqPacket.setTo(entity);
        iqPacket.addChild(new ItemsQuery());
        final var future = connection.sendIqPacket(iqPacket);
        return Futures.transform(
                future,
                iqResult -> {
                    final var itemsQuery = iqResult.getExtension(ItemsQuery.class);
                    if (itemsQuery == null) {
                        throw new IllegalStateException();
                    }
                    // TODO store items
                    final var items = itemsQuery.getExtensions(Item.class);
                    return items;
                },
                MoreExecutors.directExecutor());
    }
}
