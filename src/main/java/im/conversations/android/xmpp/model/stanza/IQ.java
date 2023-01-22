package im.conversations.android.xmpp.model.stanza;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import java.util.Locale;

@XmlElement
public class IQ extends Stanza {

    public IQ() {
        super(IQ.class);
    }

    public IQ(final Type type) {
        super(IQ.class);
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
