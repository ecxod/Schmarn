package im.conversations.android.xmpp.model.retract;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Retract extends Extension {

    public Retract() {
        super(Retract.class);
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setId(final String id) {
        this.setAttribute("id", id);
    }
}
