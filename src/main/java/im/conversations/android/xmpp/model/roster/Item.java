package im.conversations.android.xmpp.model.roster;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Item extends Extension {

    public Item() {
        super("item", Namespace.ROSTER);
    }
}
