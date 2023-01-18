package im.conversations.android.xmpp.model.streams;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.capabilties.EntityCapabilities;

@XmlElement
public class Features extends Extension implements EntityCapabilities {
    public Features() {
        super(Features.class);
    }

    public boolean streamManagement() {
        // TODO use hasExtension
        return this.hasChild("sm", Namespace.STREAM_MANAGEMENT);
    }

    public boolean invite() {
        return this.hasChild("register", Namespace.INVITE);
    }

    public boolean clientStateIndication() {
        return this.hasChild("csi", Namespace.CSI);
    }
}
