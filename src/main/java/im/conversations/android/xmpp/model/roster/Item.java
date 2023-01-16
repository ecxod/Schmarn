package im.conversations.android.xmpp.model.roster;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@XmlElement
public class Item extends Extension {

    public static final List<Subscription> RESULT_SUBSCRIPTIONS =
            Arrays.asList(Subscription.NONE, Subscription.TO, Subscription.FROM, Subscription.BOTH);

    public Item() {
        super(Item.class);
    }

    public Jid getJid() {
        return getAttributeAsJid("jid");
    }

    public String getItemName() {
        return this.getAttribute("name");
    }

    public boolean isPendingOut() {
        return "subscribe".equalsIgnoreCase(this.getAttribute("ask"));
    }

    public Subscription getSubscription() {
        final String value = this.getAttribute("subscription");
        try {
            return value == null ? null : Subscription.valueOf(value.toLowerCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public enum Subscription {
        NONE,
        TO,
        FROM,
        BOTH,
        REMOVE
    }
}
