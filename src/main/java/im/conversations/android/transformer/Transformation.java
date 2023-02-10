package im.conversations.android.transformer;

import androidx.annotation.NonNull;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.DeliveryReceipt;
import im.conversations.android.xmpp.model.DeliveryReceiptRequest;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.jabber.Thread;
import im.conversations.android.xmpp.model.muc.user.MultiUserChat;
import im.conversations.android.xmpp.model.oob.OutOfBandData;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Transformation {

    private static final List<Class<? extends Extension>> EXTENSION_FOR_TRANSFORMATION =
            Arrays.asList(
                    Body.class,
                    Thread.class,
                    Encrypted.class,
                    OutOfBandData.class,
                    DeliveryReceipt.class,
                    MultiUserChat.class);

    public final Instant receivedAt;
    public final Jid to;
    public final Jid from;
    public final Jid remote;
    public final Message.Type type;
    public final String messageId;
    public final String stanzaId;

    private final List<Extension> extensions;

    public final Collection<DeliveryReceiptRequest> deliveryReceiptRequests;

    private Transformation(
            final Instant receivedAt,
            final Jid to,
            final Jid from,
            final Jid remote,
            final Message.Type type,
            final String messageId,
            final String stanzaId,
            final List<Extension> extensions,
            final Collection<DeliveryReceiptRequest> deliveryReceiptRequests) {
        this.receivedAt = receivedAt;
        this.to = to;
        this.from = from;
        this.remote = remote;
        this.type = type;
        this.messageId = messageId;
        this.stanzaId = stanzaId;
        this.extensions = extensions;
        this.deliveryReceiptRequests = deliveryReceiptRequests;
    }

    public boolean isAnythingToTransform() {
        return this.extensions.size() > 0;
    }

    public Jid fromBare() {
        return from == null ? null : from.asBareJid();
    }

    public String fromResource() {
        return from == null ? null : from.getResource();
    }

    public Jid toBare() {
        return to == null ? null : to.asBareJid();
    }

    public String toResource() {
        return to == null ? null : to.getResource();
    }

    public Instant sentAt() {
        // TODO get Delay that matches sender; return receivedAt if not found
        return receivedAt;
    }

    public boolean outgoing() {
        // TODO handle case for self addressed (to == from)
        return remote.asBareJid().equals(toBare());
    }

    public <E extends Extension> E getExtension(final Class<E> clazz) {
        final var extension = Iterables.find(this.extensions, clazz::isInstance, null);
        return extension == null ? null : clazz.cast(extension);
    }

    public <E extends Extension> Collection<E> getExtensions(final Class<E> clazz) {
        return Collections2.transform(
                Collections2.filter(this.extensions, clazz::isInstance), clazz::cast);
    }

    public static Transformation of(
            @NonNull final Message message,
            @NonNull final Instant receivedAt,
            @NonNull final Jid remote,
            final String stanzaId) {
        final var to = message.getTo();
        final var from = message.getFrom();
        final var type = message.getType();
        final var messageId = message.getId();
        final ImmutableList.Builder<Extension> extensionListBuilder = new ImmutableList.Builder<>();
        for (final Class<? extends Extension> clazz : EXTENSION_FOR_TRANSFORMATION) {
            extensionListBuilder.addAll(message.getExtensions(clazz));
        }
        final var requests = message.getExtensions(DeliveryReceiptRequest.class);
        return new Transformation(
                receivedAt,
                to,
                from,
                remote,
                type,
                messageId,
                stanzaId,
                extensionListBuilder.build(),
                requests);
    }
}
