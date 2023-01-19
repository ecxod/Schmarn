package im.conversations.android.xmpp.model;

import eu.siacs.conversations.xml.Element;
import im.conversations.android.xmpp.ExtensionFactory;

public class Extension extends Element {

    private Extension(final ExtensionFactory.Id id) {
        super(id.name, id.namespace);
    }

    public Extension(final Class<? extends Extension> clazz) {
        this(ExtensionFactory.id(clazz));
    }
}
