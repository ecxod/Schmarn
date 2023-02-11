package im.conversations.android.xmpp.processor;

import android.content.Context;
import im.conversations.android.database.ConversationsDatabase;
import im.conversations.android.transformer.TransformationFactory;
import im.conversations.android.transformer.Transformer;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.ArchiveManager;
import im.conversations.android.xmpp.manager.CarbonsManager;
import im.conversations.android.xmpp.manager.ChatStateManager;
import im.conversations.android.xmpp.manager.PubSubManager;
import im.conversations.android.xmpp.manager.ReceiptManager;
import im.conversations.android.xmpp.manager.StanzaIdManager;
import im.conversations.android.xmpp.model.carbons.Received;
import im.conversations.android.xmpp.model.carbons.Sent;
import im.conversations.android.xmpp.model.mam.Result;
import im.conversations.android.xmpp.model.pubsub.event.Event;
import im.conversations.android.xmpp.model.stanza.Message;
import im.conversations.android.xmpp.model.state.ChatStateNotification;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageProcessor extends XmppConnection.Delegate implements Consumer<Message> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);

    private final Level level;
    private final TransformationFactory transformationFactory;

    public MessageProcessor(final Context context, final XmppConnection connection) {
        this(context, connection, Level.ROOT);
    }

    public MessageProcessor(
            final Context context, final XmppConnection connection, final Level level) {
        super(context, connection);
        this.level = level;
        this.transformationFactory = new TransformationFactory(context, connection);
    }

    @Override
    public void accept(final Message message) {

        if (isRoot() && connection.fromServer(message) && message.hasExtension(Received.class)) {
            getManager(CarbonsManager.class).handleReceived(message.getExtension(Received.class));
            return;
        }

        if (isRoot() && connection.fromServer(message) && message.hasExtension(Sent.class)) {
            getManager(CarbonsManager.class).handleSent(message.getExtension(Sent.class));
            return;
        }

        if (isRoot() && message.hasExtension(Event.class)) {
            getManager(PubSubManager.class).handleEvent(message);
            return;
        }

        if (isRoot() && message.hasExtension(Result.class)) {
            getManager(ArchiveManager.class).handle(message);
            return;
        }

        // LOGGER.info("Message from {} with {}", message.getFrom(), message.getExtensionIds());

        final var from = message.getFrom();

        final var id = message.getId();
        final var stanzaId = getManager(StanzaIdManager.class).getStanzaId(message);
        final var transformation = transformationFactory.create(message, stanzaId);
        final boolean sendReceipts;
        if (transformation.isAnythingToTransform()) {
            final var database = ConversationsDatabase.getInstance(context);
            final var transformer = new Transformer(database, getAccount());
            sendReceipts = transformer.transform(transformation);
        } else {
            sendReceipts = true;
        }
        if (sendReceipts) {
            getManager(ReceiptManager.class)
                    .received(from, id, transformation.deliveryReceiptRequests);
        }
        final var chatState = message.getExtension(ChatStateNotification.class);
        if (chatState != null) {
            getManager(ChatStateManager.class).handle(from, chatState);
        }

        // TODO pass JMI to JingleManager
    }

    private boolean isRoot() {
        return this.level == Level.ROOT;
    }

    public enum Level {
        ROOT,
        CARBON,
        STANZA_CONTENT_ENCRYPTION
    }
}
