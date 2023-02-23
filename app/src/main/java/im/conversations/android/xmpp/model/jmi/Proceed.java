package im.conversations.android.xmpp.model.jmi;

import com.google.common.primitives.Ints;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xml.Element;

@XmlElement
public class Proceed extends JingleMessage {

    public Proceed() {
        super(Propose.class);
    }

    public Integer getDeviceId() {
        final Element device = this.findChild("device");
        final String id = device == null ? null : device.getAttribute("id");
        if (id == null) {
            return null;
        }
        return Ints.tryParse(id);
    }
}
