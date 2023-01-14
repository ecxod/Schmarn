package im.conversations.android.xmpp.model.roster;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "query", namespace = Namespace.ROSTER)
public class Query extends Extension {

    public Query() {
        super("query", Namespace.ROSTER);
    }

    public void setVersion(final String rosterVersion) {
        this.setAttribute("ver", rosterVersion);
    }
}
