package im.conversations.android.xmpp.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import im.conversations.android.database.model.AvatarType;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.avatar.Data;
import im.conversations.android.xmpp.model.avatar.Info;
import im.conversations.android.xmpp.model.avatar.Metadata;
import im.conversations.android.xmpp.model.pubsub.Items;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.vcard.BinaryValue;
import im.conversations.android.xmpp.model.vcard.Photo;
import im.conversations.android.xmpp.model.vcard.VCard;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvatarManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(AvatarManager.class);

    private final Map<Fetch, ListenableFuture<byte[]>> avatarFetches = new HashMap<>();

    public AvatarManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handleItems(final BareJid from, final Items items) {
        final var itemsMap = items.getItemMap(Metadata.class);
        final var firstEntry = Iterables.getFirst(itemsMap.entrySet(), null);
        if (firstEntry == null) {
            return;
        }
        final var itemId = firstEntry.getKey();
        final var metadata = firstEntry.getValue();
        final var info = metadata.getExtensions(Info.class);
        final var thumbnailOptional =
                Iterables.tryFind(info, i -> Objects.equals(itemId, i.getId()));
        if (thumbnailOptional.isPresent()) {
            final var thumbnail = thumbnailOptional.get();
            if (thumbnail.getUrl() != null) {
                LOGGER.warn(
                        "Thumbnail avatar from {} is hosted on remote URL. We require it to be"
                                + " hosted on PEP",
                        from);
                return;
            }
            final var additional =
                    Collections2.filter(
                            info,
                            i -> !Objects.equals(itemId, i.getId()) && Objects.nonNull(i.getUrl()));
            getDatabase().avatarDao().set(getAccount(), from.asBareJid(), thumbnail, additional);
        } else {
            LOGGER.warn(
                    "Avatar metadata from {} is lacking thumbnail (info.id must match item id",
                    from);
        }
    }

    public ListenableFuture<byte[]> getAvatar(
            final Jid address, final AvatarType type, final String id) {
        final var fetch = new Fetch(address, id);
        final SettableFuture<byte[]> future;
        synchronized (avatarFetches) {
            final var existing = avatarFetches.get(fetch);
            if (existing != null) {
                return existing;
            }
            future = SettableFuture.create();
            avatarFetches.put(fetch, future);
        }
        future.setFuture(getCachedOrFetch(address, type, id));
        future.addListener(
                () -> {
                    synchronized (this.avatarFetches) {
                        this.avatarFetches.remove(fetch);
                    }
                },
                MoreExecutors.directExecutor());
        return future;
    }

    public ListenableFuture<Bitmap> getAvatarBitmap(
            final Jid address, final AvatarType type, final String id) {
        return Futures.transform(
                getAvatar(address, type, id),
                bytes -> {
                    final var sourceBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    final var size = Math.min(sourceBitmap.getHeight(), sourceBitmap.getWidth());
                    // return a square version of the avatar. we are not displaying the rest and can
                    // save the bytes plus the corner radius calculation in AvatarFetcher requires a
                    // square
                    return ThumbnailUtils.extractThumbnail(sourceBitmap, size, size);
                },
                CPU_EXECUTOR);
    }

    private byte[] getCachedAvatar(final Jid address, final String id) throws IOException {
        final var cache = getCacheFile(address, id);
        final byte[] avatar = Files.toByteArray(cache);
        if (Hashing.sha1().hashBytes(avatar).toString().equalsIgnoreCase(id)) {
            LOGGER.debug("Avatar {} of {} came from cache", id, address);
            return avatar;
        } else {
            throw new IllegalStateException("Cache contained corrupted file");
        }
    }

    private ListenableFuture<byte[]> getCachedOrFetch(
            final Jid address, final AvatarType type, final String id) {
        final var cachedFuture =
                Futures.submit(() -> getCachedAvatar(address, id), FILE_IO_EXECUTOR);
        return Futures.catchingAsync(
                cachedFuture,
                Exception.class,
                exception -> fetchAndCacheAvatar(address, type, id),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<byte[]> fetchPepAvatar(final Jid address, final String id) {
        return Futures.transform(
                getManager(PubSubManager.class).fetchItem(address, id, Data.class),
                Data::asBytes,
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<byte[]> fetchVcardAvatar(final Jid address, final String id) {
        final var iq = new Iq(Iq.Type.GET);
        iq.setTo(address);
        iq.addExtension(new VCard());
        final var iqFuture = connection.sendIqPacket(iq);
        return Futures.transform(
                iqFuture,
                result -> {
                    final var vcard = result.getExtension(VCard.class);
                    if (vcard == null) {
                        throw new IllegalStateException("No vCard in response");
                    }
                    final var photo = vcard.getExtension(Photo.class);
                    final var binary = photo == null ? null : photo.getExtension(BinaryValue.class);
                    if (binary == null) {
                        throw new IllegalStateException("vCard did not have embedded photo");
                    }
                    return binary.asBytes();
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<byte[]> fetchAndCacheAvatar(
            final Jid address, final AvatarType avatarType, final String id) {
        final ListenableFuture<byte[]> avatarFetchFuture =
                switch (avatarType) {
                    case PEP -> fetchPepAvatar(address, id);
                    case VCARD -> fetchVcardAvatar(address, id);
                };
        return Futures.transform(
                avatarFetchFuture,
                avatar -> {
                    final var sha1Hash = Hashing.sha1().hashBytes(avatar).toString();
                    if (sha1Hash.equalsIgnoreCase(id)) {
                        final var cache = getCacheFile(address, id);
                        try {
                            Files.write(avatar, cache);
                        } catch (final IOException e) {
                            throw new RuntimeException("Could not store avatar", e);
                        }
                        LOGGER.info("Cached avatar {} from {}", id, address);
                        return avatar;
                    }
                    throw new IllegalStateException("Avatar sha1hash did not match expected value");
                },
                FILE_IO_EXECUTOR);
    }

    private File getCacheFile(final Jid address, final String id) {
        final var accountCacheDirectory =
                new File(context.getCacheDir(), String.valueOf(getAccount().id));
        final var userCacheDirectory =
                new File(
                        accountCacheDirectory,
                        Hashing.sha256()
                                .hashString(address.toString(), StandardCharsets.UTF_8)
                                .toString());
        if (userCacheDirectory.mkdirs()) {
            LOGGER.debug("Created directory {}", userCacheDirectory.getAbsolutePath());
        }
        return new File(userCacheDirectory, id);
    }

    private static final class Fetch {
        public final Jid address;
        public final String id;

        private Fetch(Jid address, String id) {
            this.address = address;
            this.id = id;
        }
    }
}
