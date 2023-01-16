package im.conversations.android.xmpp.model;

import eu.siacs.conversations.xml.Element;
import im.conversations.android.xmpp.Extensions;

public class Extension extends Element {

    private Extension(final Extensions.Id id) {
        super(id.name, id.namespace);
    }

    public Extension(final Class<? extends Extension> clazz) {
        this(Extensions.id(clazz));
    }
}
