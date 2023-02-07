package im.conversations.android.xmpp.model.receipts;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Received extends Extension {

    public Received() {
        super(Received.class);
    }

    public void setId(String id) {
        this.setAttribute("id", id);
    }
}
