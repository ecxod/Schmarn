package im.conversations.android.xmpp.processor;

import android.content.Context;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import im.conversations.android.database.model.PresenceShow;
import im.conversations.android.database.model.PresenceType;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.DiscoManager;
import java.util.function.Consumer;

public class PresenceProcessor extends XmppConnection.Delegate implements Consumer<PresencePacket> {

    public PresenceProcessor(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    @Override
    public void accept(final PresencePacket presencePacket) {
        final var from = presencePacket.getFrom();
        final var address = from == null ? null : from.asBareJid();
        final var resource = from == null ? null : from.getResource();
        final var typeAttribute = presencePacket.getAttribute("type");
        final PresenceType type;
        try {
            type = PresenceType.of(typeAttribute);
        } catch (final IllegalArgumentException e) {
            // log we don’t parse presence of type $type
            return;
        }
        final var show = PresenceShow.of(presencePacket.findChildContent("show"));
        final var status = presencePacket.findChildContent("status");
        getDatabase().presenceDao().set(getAccount(), address, resource, type, show, status);

        // TODO store presence info

        // TODO do this only for contacts?
        fetchCapabilities(presencePacket);
    }

    private void fetchCapabilities(final PresencePacket presencePacket) {
        final var entity = presencePacket.getFrom();
        final var nodeHash = presencePacket.getCapabilities();
        if (nodeHash != null) {
            getManager(DiscoManager.class)
                    .infoOrCache(Entity.presence(entity), nodeHash.node, nodeHash.hash);
        }
    }
}
