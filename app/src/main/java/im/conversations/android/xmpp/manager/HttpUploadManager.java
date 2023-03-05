package im.conversations.android.xmpp.manager;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import im.conversations.android.database.model.DiscoItemWithExtension;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.upload.Get;
import im.conversations.android.xmpp.model.upload.Header;
import im.conversations.android.xmpp.model.upload.Put;
import im.conversations.android.xmpp.model.upload.Request;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import org.jxmpp.jid.Jid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUploadManager extends AbstractManager {

    private static final List<String> ALLOWED_HEADERS =
            Arrays.asList("Authorization", "Cookie", "Expires");

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUploadManager.class);

    public HttpUploadManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Slot> request(
            final String filename, final long size, final MediaType mediaType) {
        return Futures.transformAsync(
                getManager(DiscoManager.class).getServerItemByFeature(Namespace.HTTP_UPLOAD),
                items -> {
                    final List<Service> services = Service.of(items);
                    final Service service =
                            Iterables.find(
                                    services,
                                    s -> s.maxFileSize == 0 || s.maxFileSize >= size,
                                    null);
                    if (service == null) {
                        throw new IllegalStateException(
                                String.format(
                                        "No upload service found that can handle files of size %d",
                                        size));
                    }
                    LOGGER.info("Requesting slot from {}", service.address);
                    return request(service.address, filename, size, mediaType);
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Slot> request(
            final Jid address, final String filename, final long size, final MediaType mediaType) {
        final var iq = new Iq(Iq.Type.GET);
        iq.setTo(address);
        final var request = iq.addExtension(new Request());
        request.setFilename(filename);
        request.setSize(size);
        request.setContentType(mediaType.type());
        final var iqFuture = connection.sendIqPacket(iq);
        // catch and rethrow 'file-too-large'
        return Futures.transform(
                iqFuture,
                result -> {
                    final var slot =
                            result.getExtension(
                                    im.conversations.android.xmpp.model.upload.Slot.class);
                    if (slot == null) {
                        throw new IllegalStateException("No slot in response");
                    }
                    final var get = slot.getExtension(Get.class);
                    final var put = slot.getExtension(Put.class);
                    final var getUrl = get == null ? null : get.getUrl();
                    final var putUrl = put == null ? null : put.getUrl();
                    if (get == null || put == null) {
                        throw new IllegalStateException("Missing put or get URL in response");
                    }
                    final ImmutableMap.Builder<String, String> headers =
                            new ImmutableMap.Builder<>();
                    for (final Header header : put.getHeaders()) {
                        final String name = header.getHeaderName();
                        final String value = header.getContent();
                        if (value != null && ALLOWED_HEADERS.contains(name)) {
                            headers.put(name, value);
                        }
                    }
                    return new Slot(putUrl, headers.buildKeepingLast(), getUrl);
                },
                MoreExecutors.directExecutor());
    }

    public static class Slot {
        public final HttpUrl put;
        public final Map<String, String> headers;
        public final HttpUrl get;

        public Slot(HttpUrl put, final Map<String, String> headers, final HttpUrl get) {
            this.put = put;
            this.headers = headers;
            this.get = get;
        }

        @NonNull
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("put", put)
                    .add("headers", headers)
                    .add("get", get)
                    .toString();
        }
    }

    private static class Service {
        public final Jid address;
        public final long maxFileSize;

        @NonNull
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("address", address)
                    .add("maxFileSize", maxFileSize)
                    .toString();
        }

        private Service(Jid address, long maxFileSize) {
            this.address = address;
            this.maxFileSize = maxFileSize;
        }

        public static List<Service> of(List<DiscoItemWithExtension> items) {
            return Lists.transform(items, Service::of);
        }

        private static Service of(final DiscoItemWithExtension item) {
            final var discoExtension = item.getExtension(Namespace.HTTP_UPLOAD);
            final long maxFileSize;
            if (discoExtension == null) {
                maxFileSize = 0;
            } else {
                final var field = discoExtension.getField("max-file-size");
                final var value = field == null ? null : Iterables.getFirst(field.values, null);
                if (Strings.isNullOrEmpty(value)) {
                    maxFileSize = 0;
                } else {
                    maxFileSize = Longs.tryParse(value);
                }
            }
            return new Service(item.address, maxFileSize);
        }
    }
}
