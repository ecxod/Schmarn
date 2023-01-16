package im.conversations.android.xmpp.model.disco.info;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Feature extends Extension {
    public Feature() {
        super(Feature.class);
    }
}
