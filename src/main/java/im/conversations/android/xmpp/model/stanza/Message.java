package im.conversations.android.xmpp.model.stanza;

import im.conversations.android.annotation.XmlElement;
import java.util.Locale;

@XmlElement
public class Message extends Stanza {

    public Message() {
        super(Message.class);
    }

    public String getBody() {
        return this.findChildContent("body");
    }

    public Type getType() {
        final var value = this.getAttribute("type");
        if (value == null) {
            return Type.NORMAL;
        } else {
            try {
                return Type.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }
    }

    public enum Type {
        ERROR,
        NORMAL,
        GROUPCHAT,
        HEADLINE,
        CHAT
    }
}
