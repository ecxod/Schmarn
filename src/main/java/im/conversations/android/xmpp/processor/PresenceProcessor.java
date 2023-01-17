package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.model.capabilties.Capabilities;
import im.conversations.android.xmpp.model.capabilties.LegacyCapabilities;
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
        final String node;
        final EntityCapabilities.Hash hash;
        final var capabilities = presencePacket.getExtension(Capabilities.class);
        final var legacyCapabilities = presencePacket.getExtension(LegacyCapabilities.class);
        if (capabilities != null) {
            node = null;
            hash = capabilities.getHash();
        } else if (legacyCapabilities != null) {
            node = legacyCapabilities.getNode();
            hash = legacyCapabilities.getHash();
        } else {
            node = null;
            hash = null;
        }
        if (hash != null) {
            getManager(DiscoManager.class).info(entity, node, hash);
        }
    }
}
