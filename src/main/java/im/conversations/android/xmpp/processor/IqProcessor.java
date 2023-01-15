package im.conversations.android.xmpp.processor;

import android.content.Context;
import com.google.common.base.Preconditions;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.BlockingManager;
import im.conversations.android.xmpp.manager.RosterManager;
import im.conversations.android.xmpp.model.blocking.Block;
import im.conversations.android.xmpp.model.blocking.Unblock;
import im.conversations.android.xmpp.model.roster.Query;
import java.util.Arrays;
import java.util.function.Consumer;

public class IqProcessor extends XmppConnection.Delegate implements Consumer<IqPacket> {

    public IqProcessor(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    @Override
    public void accept(final IqPacket packet) {
        final IqPacket.TYPE type = packet.getType();
        Preconditions.checkArgument(
                Arrays.asList(IqPacket.TYPE.GET, IqPacket.TYPE.SET).contains(type));
        if (type == IqPacket.TYPE.SET
                && connection.fromAccount(packet)
                && packet.hasExtension(Query.class)) {
            getManager(RosterManager.class).handlePush(packet.getExtension(Query.class));
            return;
        }
        if (type == IqPacket.TYPE.SET
                && connection.fromAccount(packet)
                && packet.hasExtension(Block.class)) {
            getManager(BlockingManager.class).handlePush(packet.getExtension(Block.class));
            return;
        }
        if (type == IqPacket.TYPE.SET
                && connection.fromAccount(packet)
                && packet.hasExtension(Unblock.class)) {
            getManager(BlockingManager.class).handlePush(packet.getExtension(Unblock.class));
            return;
        }
        // TODO return feature not implemented
    }
}
