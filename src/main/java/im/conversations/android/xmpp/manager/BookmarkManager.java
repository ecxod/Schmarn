package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.database.entity.BookmarkEntity;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.bookmark.Conference;
import im.conversations.android.xmpp.model.pubsub.Items;
import im.conversations.android.xmpp.model.pubsub.event.Retract;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BookmarkManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookmarkManager.class);

    public BookmarkManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void fetch() {
        final var future = getManager(PepManager.class).fetchItems(Conference.class);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Map<String, Conference> bookmarks) {
                        getDatabase().bookmarkDao().setItems(getAccount(), bookmarks);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        LOGGER.warn("Could not fetch bookmarks", throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void updateItems(final Map<String, Conference> items) {
        getDatabase().bookmarkDao().updateItems(getAccount(), items);
    }

    private void deleteItems(Collection<Retract> retractions) {
        final Collection<Jid> addresses =
                Collections2.transform(retractions, r -> BookmarkEntity.jidOrNull(r.getId()));
        getDatabase()
                .bookmarkDao()
                .delete(getAccount().id, Collections2.filter(addresses, Objects::nonNull));
    }

    public void deleteAllItems() {
        getDatabase().bookmarkDao().deleteAll(getAccount().id);
    }

    public void handleItems(final Items items) {
        final var retractions = items.getRetractions();
        final var itemMap = items.getItemMap(Conference.class);
        if (retractions.size() > 0) {
            deleteItems(retractions);
        }
        if (itemMap.size() > 0) {
            updateItems(itemMap);
        }
    }

    public ListenableFuture<Void> publishBookmark(final Jid address) {
        final var itemId = address.toEscapedString();
        final var conference = new Conference();
        return Futures.transform(
                getManager(PepManager.class)
                        .publish(conference, itemId, NodeConfiguration.WHITELIST_MAX_ITEMS),
                result -> null,
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> retractBookmark(final Jid address) {
        final var itemId = address.toEscapedString();
        return Futures.transform(
                getManager(PepManager.class).retract(itemId, Namespace.BOOKMARKS2),
                result -> null,
                MoreExecutors.directExecutor());
    }
}
