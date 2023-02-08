package im.conversations.android.transformer;

import com.google.common.collect.ImmutableList;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.DeliveryReceiptRequest;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.jabber.Thread;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Transformation {

    private static final List<Class<? extends Extension>> EXTENSION_FOR_TRANSFORMATION =
            Arrays.asList(Body.class, Thread.class, Encrypted.class);

    public final Jid to;
    public final Jid from;
    public final Message.Type type;
    public final String messageId;
    public final String stanzaId;

    private final List<Extension> extensions;

    public final Collection<DeliveryReceiptRequest> deliveryReceiptRequests;

    private Transformation(
            final Jid to,
            final Jid from,
            final Message.Type type,
            final String messageId,
            final String stanzaId,
            final List<Extension> extensions,
            final Collection<DeliveryReceiptRequest> deliveryReceiptRequests) {
        this.to = to;
        this.from = from;
        this.type = type;
        this.messageId = messageId;
        this.stanzaId = stanzaId;
        this.extensions = extensions;
        this.deliveryReceiptRequests = deliveryReceiptRequests;
    }

    public boolean isAnythingToTransform() {
        return this.extensions.size() > 0;
    }

    public static Transformation of(final Message message, final String stanzaId) {
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
                to, from, type, messageId, stanzaId, extensionListBuilder.build(), requests);
    }
}
