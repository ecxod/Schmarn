package im.conversations.android.xmpp.model.muc.user;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.muc.Affiliation;
import im.conversations.android.xmpp.model.muc.Role;
import java.util.Locale;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlElement
public class Item extends Extension {

    private static final Logger LOGGER = LoggerFactory.getLogger(Item.class);

    public Item() {
        super(Item.class);
    }

    public Affiliation getAffiliation() {
        final var affiliation = this.getAttribute("affiliation");
        if (Strings.isNullOrEmpty(affiliation)) {
            return Affiliation.NONE;
        }
        try {
            return Affiliation.valueOf(affiliation.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("could not parse affiliation {}", affiliation);
            return Affiliation.NONE;
        }
    }

    public Role getRole() {
        final var role = this.getAttribute("role");
        if (Strings.isNullOrEmpty(role)) {
            return Role.NONE;
        }
        try {
            return Role.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("could not parse role {}", role);
            return Role.NONE;
        }
    }

    public String getNick() {
        return this.getAttribute("nick");
    }

    public Jid getJid() {
        final var jid = this.getAttribute("jid");
        if (Strings.isNullOrEmpty(jid)) {
            return null;
        }
        try {
            return JidCreate.from(jid);
        } catch (final XmppStringprepException e) {
            return null;
        }
    }
}
