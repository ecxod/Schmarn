package im.conversations.android.xmpp.processor;

import android.content.Context;
import com.google.common.base.Strings;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.CarbonsManager;
import im.conversations.android.xmpp.model.carbons.Received;
import im.conversations.android.xmpp.model.carbons.Sent;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageProcessor extends XmppConnection.Delegate implements Consumer<Message> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProcessor.class);

    private final boolean isRoot;

    public MessageProcessor(final Context context, final XmppConnection connection) {
        this(context, connection, true);
    }

    public MessageProcessor(
            final Context context, final XmppConnection connection, final boolean isRoot) {
        super(context, connection);
        this.isRoot = isRoot;
    }

    @Override
    public void accept(final Message message) {

        if (isRoot && connection.fromServer(message) && message.hasExtension(Received.class)) {
            getManager(CarbonsManager.class).handleReceived(message.getExtension(Received.class));
        }

        if (isRoot && connection.fromServer(message) && message.hasExtension(Sent.class)) {
            getManager(CarbonsManager.class).handleSent(message.getExtension(Sent.class));
        }

        final String body = message.getBody();
        if (!Strings.isNullOrEmpty(body)) {
            LOGGER.info("'{}' from {}", body, message.getFrom());
        }

        // TODO process receipt requests (184 + 333)

        // TODO collect Extensions that require transformation (everything that will end up in the
        // message tables)

        // TODO pass pubsub events to pubsub manager

        // TODO pass JMI to JingleManager
    }
}
