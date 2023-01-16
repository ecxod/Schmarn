package im.conversations.android.xmpp.model.disco.info;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "query")
public class InfoQuery extends Extension {

    public InfoQuery() {
        super(InfoQuery.class);
    }
}
