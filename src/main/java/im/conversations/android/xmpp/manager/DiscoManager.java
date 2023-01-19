package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.disco.items.Item;
import im.conversations.android.xmpp.model.disco.items.ItemsQuery;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class DiscoManager extends AbstractManager {

    public DiscoManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<InfoQuery> info(final Entity entity) {
        return info(entity, null);
    }

    public ListenableFuture<Void> info(
            final Entity entity, @Nullable final String node, final EntityCapabilities.Hash hash) {
        final String capabilityNode = hash.capabilityNode(node);
        if (getDatabase().discoDao().set(getAccount(), entity, capabilityNode, hash)) {
            return Futures.immediateFuture(null);
        }
        return Futures.transform(
                info(entity, capabilityNode), f -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<InfoQuery> info(final Entity entity, final String node) {
        final var iqRequest = new IqPacket(IqPacket.TYPE.GET);
        iqRequest.setTo(entity.address);
        final var infoQueryRequest = new InfoQuery();
        if (node != null) {
            infoQueryRequest.setNode(node);
        }
        iqRequest.addChild(infoQueryRequest);
        final var future = connection.sendIqPacket(iqRequest);
        // TODO we need to remove the disco info associated with $entity in case of failure
        // this might happen in (rather unlikely) scenarios where an item no longer speaks disco
        return Futures.transform(
                future,
                iqResult -> {
                    final var infoQuery = iqResult.getExtension(InfoQuery.class);
                    if (infoQuery == null) {
                        throw new IllegalStateException("Response did not have query child");
                    }
                    if (!Objects.equals(node, infoQuery.getNode())) {
                        throw new IllegalStateException(
                                "Node in response did not match node in request");
                    }
                    final byte[] caps = EntityCapabilities.hash(infoQuery).hash;
                    final byte[] caps2 = EntityCapabilities2.hash(infoQuery).hash;
                    getDatabase()
                            .discoDao()
                            .set(getAccount(), entity, node, caps, caps2, infoQuery);
                    return infoQuery;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Collection<Item>> items(final Entity.DiscoItem entity) {
        final var iqPacket = new IqPacket(IqPacket.TYPE.GET);
        iqPacket.setTo(entity.address);
        iqPacket.addChild(new ItemsQuery());
        final var future = connection.sendIqPacket(iqPacket);
        return Futures.transform(
                future,
                iqResult -> {
                    final var itemsQuery = iqResult.getExtension(ItemsQuery.class);
                    if (itemsQuery == null) {
                        throw new IllegalStateException();
                    }
                    final var items = itemsQuery.getExtensions(Item.class);
                    final var validItems =
                            Collections2.filter(items, i -> Objects.nonNull(i.getJid()));
                    getDatabase().discoDao().set(getAccount(), entity, validItems);
                    return validItems;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<List<InfoQuery>> itemsWithInfo(final Entity.DiscoItem entity) {
        final var itemsFutures = items(entity);
        return Futures.transformAsync(
                itemsFutures,
                items -> {
                    final var filtered =
                            Collections2.filter(items, i -> Objects.nonNull(i.getJid()));
                    Collection<ListenableFuture<InfoQuery>> infoFutures =
                            Collections2.transform(
                                    filtered, i -> info(Entity.discoItem(i.getJid()), i.getNode()));
                    return Futures.allAsList(infoFutures);
                },
                MoreExecutors.directExecutor());
    }

    public boolean hasFeature(final Jid entity, final String feature) {
        return getDatabase().discoDao().hasFeature(getAccount().id, entity, feature);
    }
}
