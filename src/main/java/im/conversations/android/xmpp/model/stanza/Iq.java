package im.conversations.android.xmpp.model.stanza;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import java.util.Locale;

@XmlElement
public class Iq extends Stanza {

    public Iq() {
        super(Iq.class);
    }

    public Iq(final Type type) {
        super(Iq.class);
        this.setAttribute("type", type.toString().toLowerCase(Locale.ROOT));
    }

    // TODO get rid of timeout
    public enum Type {
        SET,
        GET,
        ERROR,
        RESULT,
        TIMEOUT
    }

    public Type getType() {
        return Type.valueOf(
                Strings.nullToEmpty(this.getAttribute("type")).toUpperCase(Locale.ROOT));
    }
}
