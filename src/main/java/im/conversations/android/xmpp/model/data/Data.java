package im.conversations.android.xmpp.model.data;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "x")
public class Data extends Extension {
    public Data() {
        super(Data.class);
    }
}
