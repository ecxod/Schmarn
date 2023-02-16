package im.conversations.android.xmpp.model.reply;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.model.Extension;
import org.jxmpp.jid.Jid;

@XmlElement(namespace = Namespace.REPLY)
public class Reply extends Extension {

    public Reply() {
        super(Reply.class);
    }

    public Jid getTo() {
        return this.getAttributeAsJid("to");
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setTo(final Jid to) {
        this.setAttribute("to", to);
    }

    public void setId(final String id) {
        this.setAttribute("id", id);
    }
}
