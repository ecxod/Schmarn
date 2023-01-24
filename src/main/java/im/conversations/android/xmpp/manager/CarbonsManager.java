package im.conversations.android.xmpp.manager;

import android.content.Context;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.carbons.Enable;
import im.conversations.android.xmpp.model.carbons.Received;
import im.conversations.android.xmpp.model.carbons.Sent;
import im.conversations.android.xmpp.model.stanza.IQ;
import im.conversations.android.xmpp.processor.MessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarbonsManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CarbonsManager.class);

    private final MessageProcessor messageProcessor;

    private boolean enabled = false;

    public CarbonsManager(Context context, XmppConnection connection) {
        super(context, connection);
        this.messageProcessor = new MessageProcessor(context, connection, false);
    }

    public void enable() {
        final var iq = new IQ(IQ.Type.SET);
        iq.addExtension(new Enable());
        connection.sendIqPacket(
                iq,
                result -> {
                    if (result.getType() == IQ.Type.RESULT) {
                        LOGGER.info("{}: successfully enabled carbons", getAccount().address);
                        this.enabled = true;
                    } else {
                        LOGGER.warn(
                                "{}: could not enable carbons {}", getAccount().address, result);
                    }
                });
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void handleReceived(final Received received) {
        final var forwarded = received.getForwarded();
        final var message = forwarded == null ? null : forwarded.getMessage();
        if (message == null) {
            LOGGER.warn("Received carbon copy did not contain forwarded message");
        } else if (connection.toAccount(message)) {
            // all received, forwarded messages must be addressed to us
            this.messageProcessor.accept(message);
        } else {
            LOGGER.warn("Received carbon copy had invalid `to` attribute {}", message.getTo());
        }
    }

    public void handleSent(final Sent sent) {
        final var forwarded = sent.getForwarded();
        final var message = forwarded == null ? null : forwarded.getMessage();
        if (message == null) {
            LOGGER.warn("Sent carbon copy did not contain forwarded message");
        } else if (connection.fromAccount(message)) {
            // all sent, forwarded messages must be addressed from us
            this.messageProcessor.accept(message);
        } else {
            LOGGER.warn("Sent carbon copy had invalid `from` attribute {}", message.getFrom());
        }
    }
}
