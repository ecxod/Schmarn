package im.conversations.android.xmpp.model.blocking;

import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Item extends Extension {

    public Item() {
        super("item", Namespace.BLOCKING);
    }

    public Jid getJid() {
        return getAttributeAsJid("jid");
    }
}
