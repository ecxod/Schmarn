package im.conversations.android.transformer;

import androidx.annotation.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import im.conversations.android.xmpp.model.DeliveryReceipt;
import im.conversations.android.xmpp.model.DeliveryReceiptRequest;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.axolotl.Encrypted;
import im.conversations.android.xmpp.model.correction.Replace;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.jabber.Thread;
import im.conversations.android.xmpp.model.markers.Displayed;
import im.conversations.android.xmpp.model.muc.user.MucUser;
import im.conversations.android.xmpp.model.oob.OutOfBandData;
import im.conversations.android.xmpp.model.reactions.Reactions;
import im.conversations.android.xmpp.model.reply.Reply;
import im.conversations.android.xmpp.model.retract.Retract;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;

public class MessageTransformation extends Transformation {

    private static final List<Class<? extends Extension>> EXTENSION_FOR_TRANSFORMATION =
            Arrays.asList(
                    Body.class,
                    Thread.class,
                    Encrypted.class,
                    OutOfBandData.class,
                    DeliveryReceipt.class,
                    MucUser.class,
                    Displayed.class,
                    Replace.class,
                    Reactions.class,
                    Reply.class,
                    Retract.class);

    private final BareJid senderIdentity;

    private final List<Extension> extensions;

    public final Collection<DeliveryReceiptRequest> deliveryReceiptRequests;

    private MessageTransformation(
            final Instant receivedAt,
            final Jid to,
            final Jid from,
            final Jid remote,
            final Message.Type type,
            final String messageId,
            final String stanzaId,
            final BareJid senderIdentity,
            final String occupantId,
            final List<Extension> extensions,
            final Collection<DeliveryReceiptRequest> deliveryReceiptRequests) {
        super(receivedAt, to, from, remote, type, messageId, stanzaId, occupantId);
        this.senderIdentity = senderIdentity;
        this.extensions = extensions;
        this.deliveryReceiptRequests = deliveryReceiptRequests;
    }

    public boolean isAnythingToTransform() {
        return this.extensions.size() > 0;
    }

    @Override
    public Instant sentAt() {
        // TODO get Delay that matches sender; return receivedAt if not found
        return receivedAt;
    }

    public BareJid senderIdentity() {
        return senderIdentity;
    }

    public <E extends Extension> E getExtension(final Class<E> clazz) {
        checkArgument(clazz);
        final var extension = Iterables.find(this.extensions, clazz::isInstance, null);
        return extension == null ? null : clazz.cast(extension);
    }

    private void checkArgument(final Class<? extends Extension> clazz) {
        if (EXTENSION_FOR_TRANSFORMATION.contains(clazz) || clazz == Error.class) {
            return;
        }
        throw new IllegalArgumentException(
                String.format("%s has not been registered for transformation", clazz.getName()));
    }

    public <E extends Extension> Collection<E> getExtensions(final Class<E> clazz) {
        checkArgument(clazz);
        return Collections2.transform(
                Collections2.filter(this.extensions, clazz::isInstance), clazz::cast);
    }

    public static MessageTransformation of(
            @NonNull final Message message,
            @NonNull final Instant receivedAt,
            @NonNull final Jid remote,
            final String stanzaId,
            final BareJid senderId,
            final String occupantId) {
        final var to = message.getTo();
        final var from = message.getFrom();
        final var type = message.getType();
        final var messageId = message.getId();
        final ImmutableList.Builder<Extension> extensionListBuilder = new ImmutableList.Builder<>();
        final Collection<DeliveryReceiptRequest> requests;
        if (type != Message.Type.GROUPCHAT) {
            Preconditions.checkNotNull(
                    senderId, "senderId must not be null for anything but group chat messages");
        }
        if (type == Message.Type.ERROR) {
            extensionListBuilder.add(message.getError());
            requests = Collections.emptyList();
        } else {
            for (final Class<? extends Extension> clazz : EXTENSION_FOR_TRANSFORMATION) {
                extensionListBuilder.addAll(message.getExtensions(clazz));
            }
            requests = message.getExtensions(DeliveryReceiptRequest.class);
        }
        return new MessageTransformation(
                receivedAt,
                to,
                from,
                remote,
                type,
                messageId,
                stanzaId,
                senderId,
                occupantId,
                extensionListBuilder.build(),
                requests);
    }
}
