package im.conversations.android.xmpp.model.disco.info;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Identity extends Extension {
    public Identity() {
        super(Identity.class);
    }
}
