package im.conversations.android.xmpp.processor;

import android.content.Context;
import im.conversations.android.database.model.PresenceShow;
import im.conversations.android.database.model.PresenceType;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.manager.MultiUserChatManager;
import im.conversations.android.xmpp.model.muc.user.MucUser;
import im.conversations.android.xmpp.model.occupant.OccupantId;
import im.conversations.android.xmpp.model.stanza.Presence;
import im.conversations.android.xmpp.model.vcard.update.VCardUpdate;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PresenceProcessor extends XmppConnection.Delegate implements Consumer<Presence> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PresenceProcessor.class);

    public PresenceProcessor(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    @Override
    public void accept(final Presence presencePacket) {
        final var from = presencePacket.getFrom();
        final var address = from == null ? null : from.asBareJid();
        if (address == null) {
            LOGGER.warn("Received presence from account (from=null). This is unusual.");
            return;
        }
        final var resource = from.getResourceOrEmpty();
        final var typeAttribute = presencePacket.getAttribute("type");
        final PresenceType type;
        try {
            type = PresenceType.of(typeAttribute);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Received presence of type '{}' from {}", typeAttribute, from);
            return;
        }
        final var show = PresenceShow.of(presencePacket.findChildContent("show"));
        final var status = presencePacket.findChildContent("status");

        final var vCardUpdate = presencePacket.getExtension(VCardUpdate.class);
        final var vCardPhoto = vCardUpdate == null ? null : vCardUpdate.getHash();
        final var muc = presencePacket.getExtension(MucUser.class);

        final String occupantId;
        if (muc != null && presencePacket.hasExtension(OccupantId.class)) {
            if (getManager(DiscoManager.class)
                    .hasFeature(Entity.discoItem(address), Namespace.OCCUPANT_ID)) {
                occupantId = presencePacket.getExtension(OccupantId.class).getId();
            } else {
                occupantId = null;
            }
        } else {
            occupantId = null;
        }

        getDatabase()
                .presenceDao()
                .set(
                        getAccount(),
                        address,
                        resource,
                        type,
                        show,
                        status,
                        vCardPhoto,
                        occupantId,
                        muc);

        final var mucManager = getManager(MultiUserChatManager.class);
        if (muc != null && muc.getStatus().contains(MucUser.STATUS_CODE_SELF_PRESENCE)) {
            if (type == null) {
                mucManager.handleSelfPresenceAvailable(presencePacket);
            } else if (type == PresenceType.UNAVAILABLE) {
                mucManager.handleSelfPresenceUnavailable(presencePacket);
            }
        }
        if (type == PresenceType.ERROR) {
            mucManager.handleErrorPresence(presencePacket);
        }

        // TODO do this only for contacts?
        fetchCapabilities(presencePacket);
    }

    private void fetchCapabilities(final Presence presencePacket) {
        final var entity = presencePacket.getFrom();
        final var nodeHash = presencePacket.getCapabilities();
        if (nodeHash != null) {
            getManager(DiscoManager.class)
                    .infoOrCache(Entity.presence(entity), nodeHash.node, nodeHash.hash);
        }
    }
}
