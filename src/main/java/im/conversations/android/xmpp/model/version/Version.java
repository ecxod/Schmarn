package im.conversations.android.xmpp.model.version;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "query", namespace = Namespace.VERSION)
public class Version extends Extension {

    public Version() {
        super(Version.class);
    }

    public void setSoftwareName(final String name) {
        this.addChild("name").setContent(name);
    }

    public void setVersion(final String version) {
        this.addChild("version").setContent(version);
    }

    public void setOs(final String os) {
        this.addChild("os").setContent(os);
    }
}
