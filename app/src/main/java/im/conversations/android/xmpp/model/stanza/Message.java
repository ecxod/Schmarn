package im.conversations.android.xmpp.model.stanza;

import im.conversations.android.annotation.XmlElement;
import java.util.Locale;

@XmlElement
public class Message extends Stanza {

    public Message() {
        super(Message.class);
    }

    public Message(Type type) {
        this();
        this.setType(type);
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

    public void setType(final Type type) {
        if (type == null || type == Type.NORMAL) {
            this.removeAttribute("type");
        } else {
            this.setAttribute("type", type.toString().toLowerCase(Locale.ROOT));
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
