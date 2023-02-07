package im.conversations.android.xmpp.model.state;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Composing extends ChatStateNotification {

    protected Composing() {
        super(Composing.class);
    }
}
