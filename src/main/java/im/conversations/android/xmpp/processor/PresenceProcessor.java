package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.DiscoManager;
import java.util.function.Consumer;

public class PresenceProcessor extends XmppConnection.Delegate implements Consumer<PresencePacket> {

    public PresenceProcessor(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    @Override
    public void accept(final PresencePacket presencePacket) {
        // TODO do this only for contacts?
        fetchCapabilities(presencePacket);
    }

    private void fetchCapabilities(final PresencePacket presencePacket) {
        final var entity = presencePacket.getFrom();
        final var nodeHash = presencePacket.getCapabilities();
        if (nodeHash != null) {
            getManager(DiscoManager.class).info(entity, nodeHash.node, nodeHash.hash);
        }
    }
}
