package im.conversations.android.xmpp.model;

import com.google.common.base.Preconditions;
import eu.siacs.conversations.xml.Element;
import im.conversations.android.xmpp.ExtensionFactory;

public class Extension extends Element {

    private Extension(final ExtensionFactory.Id id) {
        super(id.name, id.namespace);
    }

    public Extension(final Class<? extends Extension> clazz) {
        this(
                Preconditions.checkNotNull(
                        ExtensionFactory.id(clazz),
                        String.format(
                                "%s does not seem to be annotated with @XmlElement",
                                clazz.getName())));
    }
}
