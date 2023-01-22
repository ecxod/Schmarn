package im.conversations.android.xmpp.model.stanza;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Message extends Stanza {

    public Message() {
        super(Message.class);
    }
}
