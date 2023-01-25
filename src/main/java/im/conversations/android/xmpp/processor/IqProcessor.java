package im.conversations.android.xmpp.processor;

import android.content.Context;
import com.google.common.base.Preconditions;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.BlockingManager;
import im.conversations.android.xmpp.manager.RosterManager;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.blocking.Block;
import im.conversations.android.xmpp.model.blocking.Unblock;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.ping.Ping;
import im.conversations.android.xmpp.model.roster.Query;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Arrays;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IqProcessor extends XmppConnection.Delegate implements Consumer<Iq> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IqProcessor.class);

    public IqProcessor(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    @Override
    public void accept(final Iq packet) {
        final Iq.Type type = packet.getType();
        Preconditions.checkArgument(Arrays.asList(Iq.Type.GET, Iq.Type.SET).contains(type));
        if (type == Iq.Type.SET
                && connection.fromAccount(packet)
                && packet.hasExtension(Query.class)) {
            getManager(RosterManager.class).handlePush(packet.getExtension(Query.class));
            sendResultFor(packet);
            return;
        }
        if (type == Iq.Type.SET
                && connection.fromAccount(packet)
                && packet.hasExtension(Block.class)) {
            getManager(BlockingManager.class).handlePush(packet.getExtension(Block.class));
            sendResultFor(packet);
            return;
        }
        if (type == Iq.Type.SET
                && connection.fromAccount(packet)
                && packet.hasExtension(Unblock.class)) {
            getManager(BlockingManager.class).handlePush(packet.getExtension(Unblock.class));
            sendResultFor(packet);
            return;
        }
        if (type == Iq.Type.GET && packet.hasExtension(Ping.class)) {
            LOGGER.debug("Responding to ping from {}", packet.getFrom());
            sendResultFor(packet);
            return;
        }

        final var extensionIds = packet.getExtensionIds();
        LOGGER.info("Could not handle {}. Sending feature-not-implemented", extensionIds);
        sendErrorFor(packet, new Condition.FeatureNotImplemented());
    }

    public void sendResultFor(final Iq request, final Extension... extensions) {
        final var from = request.getFrom();
        final var id = request.getId();
        final var response = new Iq(Iq.Type.RESULT);
        response.setTo(from);
        response.setId(id);
        for (final Extension extension : extensions) {
            response.addExtension(extension);
        }
        connection.sendIqPacket(response);
    }

    public void sendErrorFor(final Iq request, final Condition condition) {
        final var from = request.getFrom();
        final var id = request.getId();
        final var response = new Iq(Iq.Type.ERROR);
        response.setTo(from);
        response.setId(id);
        final Error error = response.addExtension(new Error());
        error.setCondition(condition);
        connection.sendIqPacket(response);
    }
}
