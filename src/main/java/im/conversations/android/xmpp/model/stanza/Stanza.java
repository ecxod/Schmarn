package im.conversations.android.xmpp.model.stanza;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.StreamElement;
import im.conversations.android.xmpp.model.error.Error;

public abstract class Stanza extends StreamElement {

    protected Stanza(Class<? extends Extension> clazz) {
        super(clazz);
    }

    public Jid getTo() {
        return this.getAttributeAsJid("to");
    }

    public Jid getFrom() {
        return this.getAttributeAsJid("from");
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setId(final String id) {
        this.setAttribute("id", id);
    }

    public void setFrom(final Jid from) {
        this.setAttribute("from", from);
    }

    public void setTo(final Jid to) {
        this.setAttribute("to", to);
    }

    public Error getError() {
        return this.getExtension(Error.class);
    }
}
