package im.conversations.android.xmpp.manager;

import android.content.Context;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.ServiceDescription;
import im.conversations.android.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.capabilties.Capabilities;
import im.conversations.android.xmpp.model.capabilties.LegacyCapabilities;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PresenceManager extends AbstractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PresenceManager.class);

    private final Map<EntityCapabilities.Hash, ServiceDescription> serviceDescriptions =
            new HashMap<>();

    public PresenceManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void sendPresence() {
        final var serviceDiscoveryFeatures = getManager(DiscoManager.class).getServiceDescription();
        final var infoQuery = serviceDiscoveryFeatures.asInfoQuery();
        final var capsHash = EntityCapabilities.hash(infoQuery);
        final var caps2Hash = EntityCapabilities2.hash(infoQuery);
        serviceDescriptions.put(capsHash, serviceDiscoveryFeatures);
        serviceDescriptions.put(caps2Hash, serviceDiscoveryFeatures);
        final var capabilities = new Capabilities();
        capabilities.setHash(caps2Hash);
        final var legacyCapabilities = new LegacyCapabilities();
        legacyCapabilities.setNode(DiscoManager.CAPABILITY_NODE);
        legacyCapabilities.setHash(capsHash);
        final var presence = new Presence();
        presence.addExtension(capabilities);
        presence.addExtension(legacyCapabilities);

        LOGGER.info(presence.toString());

        connection.sendPresencePacket(presence);
    }

    public ServiceDescription getCachedServiceDescription(final EntityCapabilities.Hash hash) {
        return this.serviceDescriptions.get(hash);
    }
}
