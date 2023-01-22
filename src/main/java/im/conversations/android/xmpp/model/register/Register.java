package im.conversations.android.xmpp.model.register;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "query")
public class Register extends Extension {

    public Register() {
        super(Register.class);
    }
}
