package im.conversations.android.xmpp.model.error;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xml.Namespace;
import im.conversations.android.xmpp.model.Extension;
import java.util.Locale;

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

    public void setType(final Type type) {
        this.setAttribute("type", type.toString().toLowerCase(Locale.ROOT));
    }

    public enum Type {
        MODIFY,
        CANCEL,
        AUTH,
        WAIT
    }
}
