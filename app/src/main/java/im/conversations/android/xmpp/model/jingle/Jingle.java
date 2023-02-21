package im.conversations.android.xmpp.model.jingle;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Jingle extends Extension {


    public Jingle() {
        super(Jingle.class);
    }
}
