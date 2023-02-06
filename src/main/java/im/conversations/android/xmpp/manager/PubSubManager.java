package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.IqErrorException;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.PreconditionNotMetException;
import im.conversations.android.xmpp.PubSubErrorException;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.pubsub.Items;
import im.conversations.android.xmpp.model.pubsub.PubSub;
import im.conversations.android.xmpp.model.pubsub.PublishOptions;
import im.conversations.android.xmpp.model.pubsub.error.PubSubError;
import im.conversations.android.xmpp.model.pubsub.event.Event;
import im.conversations.android.xmpp.model.pubsub.event.Purge;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PubSubManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PubSubManager.class);

    private static final String SINGLETON_ITEM_ID = "current";

    public PubSubManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handleEvent(final Message message) {
        final var event = message.getExtension(Event.class);
        if (event.hasExtension(Purge.class)) {
            handlePurge(message);
        } else if (event.hasExtension(Event.ItemsWrapper.class)) {
            handleItems(message);
        }
    }

    public <T extends Extension> ListenableFuture<Map<String, T>> fetchItems(
            final Jid address, final Class<T> clazz) {
        final var id = ExtensionFactory.id(clazz);
        if (id == null) {
            return Futures.immediateFailedFuture(
                    new IllegalArgumentException(
                            String.format("%s is not a registered extension", clazz.getName())));
        }
        return fetchItems(address, id.namespace, clazz);
    }

    public <T extends Extension> ListenableFuture<Map<String, T>> fetchItems(
            final Jid address, final String node, final Class<T> clazz) {
        final Iq request = new Iq(Iq.Type.GET);
        request.setTo(address);
        final var pubSub = request.addExtension(new PubSub());
        final var itemsWrapper = pubSub.addExtension(new PubSub.ItemsWrapper());
        itemsWrapper.setNode(node);
        return Futures.transform(
                connection.sendIqPacket(request),
                response -> {
                    final var pubSubResponse = response.getExtension(PubSub.class);
                    if (pubSubResponse == null) {
                        throw new IllegalStateException();
                    }
                    final var items = pubSubResponse.getItems();
                    if (items == null) {
                        throw new IllegalStateException();
                    }
                    return items.getItemMap(clazz);
                },
                MoreExecutors.directExecutor());
    }

    public <T extends Extension> ListenableFuture<T> fetchItem(
            final Jid address, final String itemId, final Class<T> clazz) {
        final var id = ExtensionFactory.id(clazz);
        if (id == null) {
            return Futures.immediateFailedFuture(
                    new IllegalArgumentException(
                            String.format("%s is not a registered extension", clazz.getName())));
        }
        return fetchItem(address, id.namespace, itemId, clazz);
    }

    public <T extends Extension> ListenableFuture<T> fetchItem(
            final Jid address, final String node, final String itemId, final Class<T> clazz) {
        final Iq request = new Iq(Iq.Type.GET);
        request.setTo(address);
        final var pubSub = request.addExtension(new PubSub());
        final var itemsWrapper = pubSub.addExtension(new PubSub.ItemsWrapper());
        itemsWrapper.setNode(node);
        final var item = itemsWrapper.addExtension(new PubSub.Item());
        item.setId(itemId);
        return Futures.transform(
                connection.sendIqPacket(request),
                response -> {
                    final var pubSubResponse = response.getExtension(PubSub.class);
                    if (pubSubResponse == null) {
                        throw new IllegalStateException();
                    }
                    final var items = pubSubResponse.getItems();
                    if (items == null) {
                        throw new IllegalStateException();
                    }
                    return items.getItemOrThrow(itemId, clazz);
                },
                MoreExecutors.directExecutor());
    }

    public <T extends Extension> ListenableFuture<T> fetchMostRecentItem(
            final Jid address, final String node, final Class<T> clazz) {
        final Iq request = new Iq(Iq.Type.GET);
        request.setTo(address);
        final var pubSub = request.addExtension(new PubSub());
        final var itemsWrapper = pubSub.addExtension(new PubSub.ItemsWrapper());
        itemsWrapper.setNode(node);
        itemsWrapper.setMaxItems(1);
        return Futures.transform(
                connection.sendIqPacket(request),
                response -> {
                    final var pubSubResponse = response.getExtension(PubSub.class);
                    if (pubSubResponse == null) {
                        throw new IllegalStateException();
                    }
                    final var items = pubSubResponse.getItems();
                    if (items == null) {
                        throw new IllegalStateException();
                    }
                    return items.getOnlyItem(clazz);
                },
                MoreExecutors.directExecutor());
    }

    private void handleItems(final Message message) {
        final var from = message.getFrom();
        final var event = message.getExtension(Event.class);
        final Items items = event.getItems();
        final var node = items.getNode();
        if (connection.fromAccount(message) && Namespace.BOOKMARKS2.equals(node)) {
            getManager(BookmarkManager.class).handleItems(items);
            return;
        }
        if (Namespace.AVATAR_METADATA.equals(node)) {
            getManager(AvatarManager.class).handleItems(from, items);
            return;
        }
        if (Namespace.NICK.equals(node)) {
            getManager(NickManager.class).handleItems(from, items);
            return;
        }
        if (Namespace.AXOLOTL_DEVICE_LIST.equals(node)) {
            getManager(AxolotlManager.class).handleItems(from, items);
        }
    }

    private void handlePurge(final Message message) {
        final var from = message.getFrom();
        final var event = message.getExtension(Event.class);
        final var purge = event.getPurge();
        final var node = purge.getNode();
        if (connection.fromAccount(message) && Namespace.BOOKMARKS2.equals(node)) {
            getManager(BookmarkManager.class).deleteAllItems();
        }
    }

    public ListenableFuture<Void> publishSingleton(
            Jid address, Extension item, final NodeConfiguration nodeConfiguration) {
        final var id = ExtensionFactory.id(item.getClass());
        return publish(address, item, SINGLETON_ITEM_ID, id.namespace, nodeConfiguration);
    }

    public ListenableFuture<Void> publishSingleton(
            Jid address,
            Extension item,
            final String node,
            final NodeConfiguration nodeConfiguration) {
        return publish(address, item, SINGLETON_ITEM_ID, node, nodeConfiguration);
    }

    public ListenableFuture<Void> publish(
            Jid address,
            Extension item,
            final String itemId,
            final NodeConfiguration nodeConfiguration) {
        final var id = ExtensionFactory.id(item.getClass());
        return publish(address, item, itemId, id.namespace, nodeConfiguration);
    }

    public ListenableFuture<Void> publish(
            final Jid address,
            final Extension itemPayload,
            final String itemId,
            final String node,
            final NodeConfiguration nodeConfiguration) {
        final var future = publishNoRetry(address, itemPayload, itemId, node, nodeConfiguration);
        return Futures.catchingAsync(
                future,
                PreconditionNotMetException.class,
                ex -> {
                    LOGGER.info("Node {} on {} requires reconfiguration", node, address);
                    final var reconfigurationFuture =
                            reconfigureNode(address, node, nodeConfiguration);
                    return Futures.transformAsync(
                            reconfigurationFuture,
                            ignored ->
                                    publishNoRetry(
                                            address, itemPayload, itemId, node, nodeConfiguration),
                            MoreExecutors.directExecutor());
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> publishNoRetry(
            final Jid address,
            final Extension itemPayload,
            final String itemId,
            final String node,
            final NodeConfiguration nodeConfiguration) {
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var pubSub = iq.addExtension(new PubSub());
        pubSub.addExtension(PublishOptions.of(nodeConfiguration));
        final var pubSubItemsWrapper = pubSub.addExtension(new PubSub.ItemsWrapper());
        pubSubItemsWrapper.setNode(node);
        final var item = pubSubItemsWrapper.addExtension(new PubSub.Item());
        item.setId(itemId);
        item.addExtension(itemPayload);
        final ListenableFuture<Void> iqFuture =
                Futures.transform(
                        connection.sendIqPacket(iq),
                        result -> null,
                        MoreExecutors.directExecutor());
        return Futures.catchingAsync(
                iqFuture,
                IqErrorException.class,
                new PubSubExceptionTransformer<>(),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> reconfigureNode(
            final Jid address, final String node, final NodeConfiguration nodeConfiguration) {
        
        return Futures.immediateVoidFuture();
    }

    private static class PubSubExceptionTransformer<V>
            implements AsyncFunction<IqErrorException, V> {

        @Override
        @NonNull
        public ListenableFuture<V> apply(@NonNull IqErrorException ex) {
            final var error = ex.getError();
            if (error == null) {
                return Futures.immediateFailedFuture(ex);
            }
            final PubSubError pubSubError = error.getExtension(PubSubError.class);
            if (pubSubError instanceof PubSubError.PreconditionNotMet) {
                return Futures.immediateFailedFuture(
                        new PreconditionNotMetException(ex.getResponse()));
            } else if (pubSubError != null) {
                return Futures.immediateFailedFuture(new PubSubErrorException(ex.getResponse()));
            } else {
                return Futures.immediateFailedFuture(ex);
            }
        }
    }
}
