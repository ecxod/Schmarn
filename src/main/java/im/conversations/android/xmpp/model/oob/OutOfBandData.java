package im.conversations.android.xmpp.model.oob;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "x")
public class OutOfBandData extends Extension {

    public OutOfBandData() {
        super(OutOfBandData.class);
    }

    public String getURL() {
        final URL url = this.getExtension(URL.class);
        return url == null ? null : url.getContent();
    }
}
