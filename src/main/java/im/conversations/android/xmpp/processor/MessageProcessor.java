package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.CarbonsManager;
import im.conversations.android.xmpp.manager.ChatStateManager;
import im.conversations.android.xmpp.manager.PubSubManager;
import im.conversations.android.xmpp.manager.ReceiptManager;
import im.conversations.android.xmpp.model.DeliveryReceiptRequest;
import im.conversations.android.xmpp.model.carbons.Received;
import im.conversations.android.xmpp.model.carbons.Sent;
import im.conversations.android.xmpp.model.pubsub.event.Event;
import im.conversations.android.xmpp.model.stanza.Message;
import im.conversations.android.xmpp.model.state.ChatStateNotification;

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageProcessor extends XmppConnection.Delegate implements Consumer<Message> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);

    private final Level level;

    public MessageProcessor(final Context context, final XmppConnection connection) {
        this(context, connection, Level.ROOT);
    }

    public MessageProcessor(
            final Context context, final XmppConnection connection, final Level level) {
        super(context, connection);
        this.level = level;
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

        final String id = message.getId();
        final Jid from = message.getFrom();

        LOGGER.info("Message received {}", message.getExtensionIds());

        // TODO only do this if transformation was successful or nothing to transform
        if (isRealtimeProcessor()) {
            final var requests = message.getExtensions(DeliveryReceiptRequest.class);
            getManager(ReceiptManager.class).received(from, id, requests);
            final var chatState = message.getExtension(ChatStateNotification.class);
            if (chatState != null) {
                getManager(ChatStateManager.class).handle(from, chatState);
            }
        }

        // TODO parse chat states

        // TODO collect Extensions that require transformation (everything that will end up in the
        // message tables)

        // TODO pass JMI to JingleManager
    }

    private boolean isRoot() {
        return this.level == Level.ROOT;
    }

    private boolean isRealtimeProcessor() {
        return this.level != Level.ARCHIVE;
    }

    public enum Level {
        ROOT,
        CARBON,
        ARCHIVE
    }
}
