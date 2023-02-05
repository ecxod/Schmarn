package im.conversations.android.xmpp.model.error;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.JABBER_CLIENT)
public class Error extends Extension {

    public Error() {
        super(Error.class);
    }

    public Condition getCondition() {
        return this.getExtension(Condition.class);
    }

    public void setCondition(final Condition condition) {
        this.addExtension(condition);
    }

    public Text getText() {
        return this.getExtension(Text.class);
    }
}
