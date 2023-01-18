package eu.siacs.conversations.xmpp.stanzas;

import im.conversations.android.xmpp.model.capabilties.EntityCapabilities;

public class PresencePacket extends AbstractAcknowledgeableStanza implements EntityCapabilities {

    public PresencePacket() {
        super("presence");
    }
}
