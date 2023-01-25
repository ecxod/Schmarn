package im.conversations.android.xmpp.processor;

import android.content.Context;
import com.google.common.base.Preconditions;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.BlockingManager;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.manager.RosterManager;
import im.conversations.android.xmpp.model.blocking.Block;
import im.conversations.android.xmpp.model.blocking.Unblock;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.error.Condition;
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
            connection.sendResultFor(packet);
            return;
        }
        if (type == Iq.Type.SET
                && connection.fromAccount(packet)
                && packet.hasExtension(Block.class)) {
            getManager(BlockingManager.class).handlePush(packet.getExtension(Block.class));
            connection.sendResultFor(packet);
            return;
        }
        if (type == Iq.Type.SET
                && connection.fromAccount(packet)
                && packet.hasExtension(Unblock.class)) {
            getManager(BlockingManager.class).handlePush(packet.getExtension(Unblock.class));
            connection.sendResultFor(packet);
            return;
        }
        if (type == Iq.Type.GET && packet.hasExtension(Ping.class)) {
            LOGGER.debug("Responding to ping from {}", packet.getFrom());
            connection.sendResultFor(packet);
            return;
        }

        if (type == Iq.Type.GET && packet.hasExtension(InfoQuery.class)) {
            getManager(DiscoManager.class).handleInfoQuery(packet);
            return;
        }

        final var extensionIds = packet.getExtensionIds();
        LOGGER.info("Could not handle {}. Sending feature-not-implemented", extensionIds);
        connection.sendErrorFor(packet, new Condition.FeatureNotImplemented());
    }
}
