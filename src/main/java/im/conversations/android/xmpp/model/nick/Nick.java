package im.conversations.android.xmpp.model.nick;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.NICK)
public class Nick extends Extension {

    public Nick() {
        super(Nick.class);
    }
}
